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

package batect.journeytests.testutils

import java.io.InputStreamReader

object DockerUtils {
    fun getAllCreatedContainers(): Set<String> {
        val commandLine = listOf("docker", "network", "ls", "--format", "{{.Name}} ({{.ID}})")
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw Exception("Retrieving list of containers from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n").toSet()
    }

    fun getAllNetworks(): Set<String> {
        val commandLine = listOf("docker", "ps", "--all", "--format", "{{.Names}} ({{.ID}}): {{.Image}}")
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw Exception("Retrieving list of networks from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n").toSet()
    }

    fun getAllVolumes(): Set<String> {
        val commandLine = listOf("docker", "volume", "ls", "-q")
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw Exception("Retrieving list of volumes from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n").toSet()
    }

    fun removeVolume(volume: String) {
        val commandLine = listOf("docker", "volume", "rm", volume)
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw Exception("Deleting volume '$volume' failed with exit code $exitCode. Output from Docker was: $output")
        }
    }

    fun removeImageIfExists(image: String) {
        val commandLine = listOf("docker", "rmi", image)
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            return
        }

        if (output.trim() == "Error: No such image: $image") {
            return
        }

        throw Exception("Deleting image '$image' failed with exit code $exitCode. Output from Docker was: $output")
    }

    fun getAllImages(): Set<String> {
        val commandLine = listOf("docker", "images", "--format", "{{ .Repository }}:{{ .Tag }}")
        val process = ProcessBuilder(commandLine)
            .redirectErrorStream(true)
            .start()

        val output = InputStreamReader(process.inputStream).readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw Exception("Retrieving list of networks from Docker failed with exit code $exitCode. Output from Docker was: $output")
        }

        return output.split("\n")
            .filterNot { it.isEmpty() }
            .toSet()
    }

    fun deleteCache(name: String) {
        getAllVolumes()
            .filter { it.startsWith("batect-cache-") && it.endsWith("-$name") }
            .forEach { removeVolume(it) }
    }
}
