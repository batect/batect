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

package batect.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File
import java.nio.file.Files

import java.nio.file.Path

abstract class UnitTestLayoutCheckTask : DefaultTask() {
    @get:InputFiles
    abstract val testFiles: Property<SourceDirectorySet>

    @get:InputFiles
    abstract val mainFiles: Property<SourceDirectorySet>

    @get:Input
    @get:Optional
    abstract val ignoreFileNameCheck: Property<FileCollection>

    @get:Input
    @get:Optional
    abstract val ignoreMissingMainFile: Property<FileCollection>

    @get:OutputFile
    abstract val upToDateFile: Property<File>

    init {
        val kotlin = project.extensions.getByType<KotlinJvmProjectExtension>()
        testFiles.set(kotlin.sourceSets.named("test").get().kotlin)
        mainFiles.set(kotlin.sourceSets.named("main").get().kotlin)

        upToDateFile.set(temporaryDir.toPath().resolve("upToDate").toFile())
    }

    @TaskAction
    fun run() {
        val testFiles = testFiles.get()
        val mainFiles = mainFiles.get()
        val testFilePaths = testFiles.files

        if (testFiles.srcDirs.size != 1) {
            throw RuntimeException("Test files source directories list has unexpected size: ${testFiles.srcDirs.size} (contents: ${testFiles.srcDirs})")
        }

        val mainKotlinFiles = mainFiles.srcDirs.filter { it.toPath().fileName.toString() == "kotlin" }

        if (mainKotlinFiles.size != 1) {
            throw RuntimeException("Main files source directories list has unexpected size: ${mainKotlinFiles.size}")
        }

        val testRoot = testFiles.srcDirs.first().toPath()
        val mainRoot = mainKotlinFiles.first().toPath()

        testFilePaths.forEach { testFile ->
            checkFile(testFile, testRoot, mainRoot)
        }

        val upToDateContents = System.currentTimeMillis().toString()
        Files.write(upToDateFile.get().toPath(), upToDateContents.toByteArray(Charsets.UTF_8))
    }

    private fun checkFile(testFile: File, testRoot: Path, mainRoot: Path) {
        if (!testFile.name.endsWith("Spec.kt")) {
            if (ignoreFileNameCheck.isPresent && ignoreFileNameCheck.get().contains(testFile)) {
                return
            }

            throw UnitTestLayoutException("File $testFile does not match the expected file name pattern. It does not end with 'Spec.kt'.")
        }

        val relativeTestFilePath = testRoot.relativize(testFile.toPath())
        val testFileName = relativeTestFilePath.fileName.toString()
        val mainFileName = testFileName.replace("Spec\\.kt\$".toRegex(), ".kt")
        val relativeMainFilePath = relativeTestFilePath.parent.resolve(mainFileName)
        val expectedMainFilePath = mainRoot.resolve(relativeMainFilePath)

        if (!expectedMainFilePath.toFile().exists()) {
            if (ignoreMissingMainFile.isPresent && ignoreMissingMainFile.get().contains(testFile)) {
                return
            }

            throw UnitTestLayoutException("Expected test file $testFile to have a corresponding main file $expectedMainFilePath, but it does not exist.")
        }
    }

    class UnitTestLayoutException(message: String) : RuntimeException(message)
}
