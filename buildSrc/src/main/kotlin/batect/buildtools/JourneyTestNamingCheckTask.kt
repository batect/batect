/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

abstract class JourneyTestNamingCheckTask : DefaultTask() {
    @get:InputFiles
    abstract val files: Property<SourceDirectorySet>

    @get:Input
    @get:Optional
    abstract val ignoreFileNameCheck: Property<FileCollection>

    @get:OutputFile
    abstract val upToDateFile: Property<File>

    init {
        upToDateFile.set(temporaryDir.toPath().resolve("upToDate").toFile())
    }

    @TaskAction
    fun run() {
        val filesToCheck = files.get().files

        filesToCheck.forEach { file ->
            checkFile(file)
        }

        val upToDateContents = System.currentTimeMillis().toString()
        Files.write(upToDateFile.get().toPath(), upToDateContents.toByteArray(Charsets.UTF_8))
    }

    private fun checkFile(file: File) {
        if (!endsWithAllowedSuffix(file)) {
            if (ignoreFileNameCheck.isPresent && ignoreFileNameCheck.get().contains(file)) {
                return
            }

            throw JourneyTestNamingException("Expected file $file to have a name ending with any of $allowedSuffixes.")
        }
    }

    private fun endsWithAllowedSuffix(file: File): Boolean {
        return allowedSuffixes.any { file.name.endsWith(it) }
    }

    class JourneyTestNamingException(message: String) : RuntimeException(message)

    companion object {
        private val allowedSuffixes = setOf("JourneyTest.kt", "JourneyTests.kt")
    }
}
