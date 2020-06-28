package batect.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

import java.nio.file.Path

class UnitTestLayoutCheckTask extends DefaultTask {
    @InputFiles
    final Property<SourceDirectorySet> testFiles

    @InputFiles
    final Property<SourceDirectorySet> mainFiles

    @Input
    @Optional
    final Property<FileCollection> ignoreFileNameCheck

    @Input
    @Optional
    final Property<FileCollection> ignoreMissingMainFile

    @OutputFile
    final Property<File> upToDateFile

    UnitTestLayoutCheckTask() {
        testFiles = project.objects.property(SourceDirectorySet.class)
        mainFiles = project.objects.property(SourceDirectorySet.class)
        ignoreFileNameCheck = project.objects.property(FileCollection.class)
        ignoreMissingMainFile = project.objects.property(FileCollection.class)
        upToDateFile = project.objects.property(File.class)

        def kotlin = project.extensions.getByType(KotlinJvmProjectExtension)
        testFiles.set(kotlin.sourceSets.named('test').get().kotlin)
        mainFiles.set(kotlin.sourceSets.named('main').get().kotlin)

        upToDateFile.set(temporaryDir.toPath().resolve("upToDate").toFile())
    }

    @TaskAction
    def run() {
        def testFiles = testFiles.get()
        def mainFiles = mainFiles.get()
        def testFilePaths = testFiles.files

        if (testFiles.srcDirs.size() != 1) {
            throw new RuntimeException("Test files source directories list has unexpected size: ${testFiles.srcDirs.size()}")
        }

        def mainKotlinFiles = mainFiles.srcDirs.grep({ it.toPath().fileName.toString() == "kotlin" })

        if (mainKotlinFiles.size() != 1) {
            println(mainKotlinFiles)
            throw new RuntimeException("Main files source directories list has unexpected size: ${mainKotlinFiles.size()}")
        }

        Path testRoot = testFiles.srcDirs.first().toPath()
        Path mainRoot = mainKotlinFiles.first().toPath()

        testFilePaths.forEach { testFile ->
            checkFile(testFile, testRoot, mainRoot)
        }

        upToDateFile.get().text = System.currentTimeMillis()
    }

    def checkFile(File testFile, Path testRoot, Path mainRoot) {
        if (!testFile.name.endsWith("Spec.kt")) {
            if (ignoreFileNameCheck.present && ignoreFileNameCheck.get().contains(testFile)) {
                return
            }

            throw new UnitTestLayoutException("File ${testFile} does not match the expected file name pattern. It does not end with 'Spec.kt'.")
        }

        def relativeTestFilePath = testRoot.relativize(testFile.toPath())
        def testFileName = relativeTestFilePath.fileName.toString()
        def mainFileName = testFileName.replaceAll("Spec\\.kt\$", ".kt")
        def relativeMainFilePath = relativeTestFilePath.parent.resolve(mainFileName)
        def expectedMainFilePath = mainRoot.resolve(relativeMainFilePath)

        if (!expectedMainFilePath.toFile().exists()) {
            if (ignoreMissingMainFile.present && ignoreMissingMainFile.get().contains(testFile)) {
                return
            }

            throw new UnitTestLayoutException("Expected test file ${testFile} to have a corresponding main file ${expectedMainFilePath}, but it does not exist.")
        }
    }

    class UnitTestLayoutException extends RuntimeException {
        UnitTestLayoutException(String message) {
            super(message)
        }
    }
}
