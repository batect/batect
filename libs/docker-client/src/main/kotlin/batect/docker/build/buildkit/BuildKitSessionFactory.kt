/*
   Copyright 2017-2020 Charles Korn.

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

package batect.docker.build.buildkit

import batect.docker.build.buildkit.services.AuthService
import batect.docker.build.buildkit.services.HealthService
import batect.logging.LoggerFactory
import batect.os.SystemInfo
import batect.telemetry.TelemetrySessionBuilder
import okio.Buffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

class BuildKitSessionFactory(
    private val systemInfo: SystemInfo,
    private val healthService: HealthService,
    private val authService: AuthService,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val loggerFactory: LoggerFactory
) {
    private val random = SecureRandom()
    private val nodeId by lazy { getOrCreateNodeID() }

    fun create(buildDirectory: Path): BuildKitSession {
        val sessionId = generateBase36Id(25)
        val services = setOf(healthService, authService)
        val listenerLogger = loggerFactory.createLoggerForClass(GrpcListener::class)
        val listener = GrpcListener(sessionId, services, listenerLogger)

        return BuildKitSession(
            sessionId,
            guaranteeNonNumeric { generateHexId(64) },
            buildDirectory.fileName.toString(),
            generateSharedKey(buildDirectory),
            listener,
            telemetrySessionBuilder
        )
    }

    private fun generateSharedKey(buildDirectory: Path): String {
        val buffer = Buffer()
        buffer.writeUtf8(nodeId)
        buffer.writeUtf8(":")
        buffer.writeUtf8(buildDirectory.toString())
        return buffer.sha256().hex()
    }

    private fun getOrCreateNodeID(): String {
        val dockerDirectory = systemInfo.homeDirectory.resolve(".docker")
        val buildNodeFilePath = dockerDirectory.resolve(".buildNodeID")

        if (!Files.exists(buildNodeFilePath)) {
            Files.createDirectories(dockerDirectory)

            val id = generateHexId(64)
            Files.write(buildNodeFilePath, id.toByteArray(Charsets.UTF_8))
        }

        return Files.readAllBytes(buildNodeFilePath).toString(Charsets.UTF_8)
    }

    private val base36: List<Char> = ('a'..'z') + ('0'..'9')
    private fun generateBase36Id(length: Int) = generateId(base36, length)

    private val hex: List<Char> = ('a'..'f') + ('0'..'9')
    private fun generateHexId(length: Int) = generateId(hex, length)

    private fun guaranteeNonNumeric(generator: () -> String): String {
        while (true) {
            val candidate = generator()

            if (candidate.any { !it.isDigit() }) {
                return candidate
            }
        }
    }

    private fun generateId(alphabet: List<Char>, length: Int): String = List(length) { alphabet.randomChar() }.joinToString("")

    private fun List<Char>.randomChar(): Char = this[random.nextInt(this.size)]
}
