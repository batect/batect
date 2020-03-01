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

package batect.cli.commands

import batect.config.ProjectPaths
import batect.docker.client.DockerVolumesClient
import batect.execution.CacheManager
import batect.execution.CacheType
import batect.ui.Console
import batect.utils.deleteDirectoryContents
import java.nio.file.Files
import kotlin.streams.toList

class CleanupCachesCommand(
    private val volumesClient: DockerVolumesClient,
    private val cacheManager: CacheManager,
    private val projectPaths: ProjectPaths,
    private val cacheType: CacheType,
    private val console: Console
) : Command {
    override fun run(): Int {
        when (cacheType) {
            CacheType.Volume -> runForVolumes()
            CacheType.Directory -> runForDirectories()
        }

        return 0
    }

    private fun runForVolumes() {
        val prefix = "batect-cache-${cacheManager.projectCacheKey}-"

        console.println("Checking for cache volumes...")

        val volumes = volumesClient.getAll()
            .filter { it.name.startsWith(prefix) }

        volumes.forEach { volume ->
            console.println("Deleting volume '${volume.name}'...")
            volumesClient.delete(volume)
        }

        if (volumes.count() == 1) {
            console.println("Done! Deleted 1 volume.")
        } else {
            console.println("Done! Deleted ${volumes.count()} volumes.")
        }
    }

    private fun runForDirectories() {
        console.println("Checking for cache directories in '${projectPaths.cacheDirectory}'...")

        val directories = Files.list(projectPaths.cacheDirectory)
            .filter { Files.isDirectory(it) }
            .toList()

        directories.forEach { directory ->
            console.println("Deleting '$directory'...")
            deleteDirectoryContents(directory)
            Files.delete(directory)
        }

        if (directories.count() == 1) {
            console.println("Done! Deleted 1 directory.")
        } else {
            console.println("Done! Deleted ${directories.count()} directories.")
        }
    }
}
