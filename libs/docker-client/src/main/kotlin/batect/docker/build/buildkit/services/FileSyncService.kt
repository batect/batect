/*
   Copyright 2017-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker.build.buildkit.services

import batect.docker.DockerException
import batect.docker.build.buildkit.GrpcEndpoint
import batect.docker.build.buildkit.ServiceInstanceFactory
import batect.docker.build.buildkit.rpcPath
import batect.logging.Logger
import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import fsutil.types.Packet
import fsutil.types.Stat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moby.filesync.v1.FileSyncBlockingServer
import okhttp3.Headers
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.streams.toList

// Original notes from BuildKit docs and code:
//
// Headers:
// - dir-name: "context" (context directory) or "dockerfile" (dockerfile directory)
// - exclude-patterns: raw exclude patterns (.dockerignore syntax)
// - include-patterns: raw include patterns, no wildcards allowed
// - override-excludes: can be ignored
// - followpaths: patterns to paths (possibly including symlinks and wildcards) that should be treated as additional include patterns - Golang implementation resolves these ahead of time to include patterns
//
// For context directory, need to reset UID and GID to 0 before sending any content
//
// 1. Send all available files and directories with packet of type PACKET_STAT
//     - if any include patterns, only send files and directories that match at least one include pattern
//     - if any exclude patterns, only send files and directories that don't match any exclude patterns
//     - original Golang implementation does some stuff to only visit directories it knows could be included / definitely won't be excluded
//     - all requestable files are assigned an ID, starting with 0
//     - requestable = not directory, not symlink, not named pipe, not socket, not device, not character device and not irregular file
//     - all files in directory should be sent (ie. don't apply rules from .dockerignore)
//     - ends with empty PACKET_STAT message
// 2. At the same time, start processing incoming messages
//     - PACKET_ERR: read data as string, throw exception and exit
//     - PACKET_REQ: read file:
//          - send response packet(s) with type PACKET_DATA, data and corresponding ID
//          - send finishing packet with type PACKET_DATA, corresponding ID and no data
//          - error if file has been requested already
//     - PACKET_FIN: respond with packet with type PACKET_FIN and exit
//

class FileSyncService(
    contextDirectory: Path,
    dockerfileDirectory: Path,
    private val statFactory: StatFactory,
    private val headers: Headers,
    private val logger: Logger
) : FileSyncBlockingServer {
    private val roots = setOf(
        FileSyncRoot("context", contextDirectory, ::resetUIDAndGID),
        FileSyncRoot("dockerfile", dockerfileDirectory)
    )

    private val rootsByName = roots.associateBy { it.name }

    // FIXME: does this need to be thread safe?
    private val paths = mutableListOf<Path>()

    override fun DiffCopy(request: MessageSource<Packet>, response: MessageSink<Packet>) {
        val directoryName = headers["dir-name"]

        if (directoryName.isNullOrEmpty()) {
            throw IllegalArgumentException("No directory name provided.")
        }

        val root = rootsByName[directoryName] ?: throw IllegalArgumentException("Unknown directory name '$directoryName'.")

        println(headers)

        val messageSink = SynchronisedMessageSink(response)
        val fileRequests = Channel<Int>(UNLIMITED)

        runBlocking(Dispatchers.Default) {
            launch { sendDirectoryContents(root, messageSink) }
            launch { handleIncomingRequests(request, messageSink, fileRequests) }
            launch { handleFileRequests(messageSink, fileRequests) }
        }
    }

    private fun sendDirectoryContents(root: FileSyncRoot, response: SynchronisedMessageSink<Packet>) {
        val patterns = headers.values("followpaths").toSet()
        walkDirectory(root, root.rootDirectory, patterns, response)
        sendDirectoryContentsEnumerationCompleted(response)
    }

    private fun walkDirectory(root: FileSyncRoot, parentPath: Path, patterns: Set<String>, response: SynchronisedMessageSink<Packet>) {
        val contents = Files.list(parentPath).use { it.toList().sorted() }

        contents.forEach { childPath ->
            val relativeChildPath = root.rootDirectory.relativize(childPath).toString()

            if (patterns.isEmpty() || patterns.contains(relativeChildPath)) {
                val stat = root.mapper(statFactory.createStat(childPath, relativeChildPath))
                paths.add(childPath)
                response.write(Packet(Packet.PacketType.PACKET_STAT, stat))
            }

            if (Files.isDirectory(childPath, LinkOption.NOFOLLOW_LINKS)) {
                walkDirectory(root, childPath, patterns, response)
            }
        }
    }

    private fun sendDirectoryContentsEnumerationCompleted(response: SynchronisedMessageSink<Packet>) {
        val statFinished = Packet(Packet.PacketType.PACKET_STAT)
        response.write(statFinished)
    }

    private suspend fun handleIncomingRequests(request: MessageSource<Packet>, response: SynchronisedMessageSink<Packet>, fileRequests: SendChannel<Int>) {
        try {
            while (true) {
                val packet = request.read() ?: return

                println("Received request packet: $packet")

                @Suppress("REDUNDANT_ELSE_IN_WHEN") // We only know about the packet types we've got in our protobuf file, but there might be others if we're talking to a newer server.
                when (packet.type) {
                    Packet.PacketType.PACKET_REQ -> fileRequests.send(packet.ID)
                    Packet.PacketType.PACKET_STAT -> throw UnsupportedOperationException("Should never receive a STAT packet from server")
                    Packet.PacketType.PACKET_DATA -> throw UnsupportedOperationException("Should never receive a DATA packet from server")
                    Packet.PacketType.PACKET_FIN -> {
                        response.write(Packet(Packet.PacketType.PACKET_FIN))
                        return
                    }
                    Packet.PacketType.PACKET_ERR -> {
                        val message = packet.data_.utf8()

                        logger.error {
                            message("Received error message during file sync.")
                            data("message", message)
                        }

                        throw DockerException("Received error message from daemon during file sync: $message")
                    }
                    else -> throw UnsupportedOperationException("Unknown packet type: ${packet.type}")
                }
            }
        } finally {
            fileRequests.close()
        }
    }

    private suspend fun handleFileRequests(response: SynchronisedMessageSink<Packet>, fileRequests: ReceiveChannel<Int>) {
        for (id in fileRequests) {
            sendFileContents(id, response)
        }
    }

    private fun sendFileContents(id: Int, response: SynchronisedMessageSink<Packet>) {
        val path = paths.getOrNull(id)

        if (path == null) {
            response.write(Packet(Packet.PacketType.PACKET_ERR, data_ = "Unknown file ID $id".encodeUtf8()))

            logger.error {
                message("Received request for file contents for unknown file ID.")
                data("id", id)
            }

            return
        }

        println("Sending data for $id ($path)")

        Files.newInputStream(path, StandardOpenOption.READ).use { stream ->
            val buffer = ByteArray(32 * 1024)

            while (true) {
                val bytesRead = stream.read(buffer)

                if (bytesRead == -1) {
                    response.write(Packet(Packet.PacketType.PACKET_DATA, ID = id))
                    return
                }

                response.write(Packet(Packet.PacketType.PACKET_DATA, ID = id, data_ = buffer.toByteString(0, bytesRead)))
            }
        }
    }

    override fun TarStream(request: MessageSource<Packet>, response: MessageSink<Packet>) {
        // This isn't used by Docker.
        throw UnsupportedGrpcMethodException(FileSyncBlockingServer::TarStream.rpcPath)
    }

    companion object {
        private fun resetUIDAndGID(stat: Stat): Stat = stat.copy(uid = 0, gid = 0)

        fun endpointsForFactory(factory: ServiceInstanceFactory<FileSyncService>): Map<String, GrpcEndpoint<*, *, *>> {
            return mapOf(
                FileSyncBlockingServer::DiffCopy.rpcPath to GrpcEndpoint(factory, FileSyncService::DiffCopy, Packet.ADAPTER, Packet.ADAPTER),
                FileSyncBlockingServer::TarStream.rpcPath to GrpcEndpoint(factory, FileSyncService::TarStream, Packet.ADAPTER, Packet.ADAPTER),
            )
        }
    }

    private data class FileSyncRoot(
        val name: String,
        val rootDirectory: Path,
        val mapper: (Stat) -> Stat = { it }
    )

    private class SynchronisedMessageSink<T : Any>(private val inner: MessageSink<T>) : MessageSink<T> {
        private val lock = Any()

        override fun cancel() {
            synchronized(lock) {
                inner.cancel()
            }
        }

        override fun close() {
            synchronized(lock) {
                inner.close()
            }
        }

        override fun write(message: T) {
            synchronized(lock) {
                println("Sending $message")
                inner.write(message)
            }
        }
    }
}
