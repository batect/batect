package batect.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class UnitTestLayoutCheck extends DefaultTask {
    @InputFiles
    final Property<SourceDirectorySet> testFiles

    @InputFiles
    final Property<SourceDirectorySet> mainFiles

    @OutputFile
    final Property<File> upToDateFile

    UnitTestLayoutCheck() {
        testFiles = project.objects.property(SourceDirectorySet.class)
        mainFiles = project.objects.property(SourceDirectorySet.class)
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

        def testRoot = testFiles.srcDirs.first().toPath()
        def mainRoot = mainKotlinFiles.first().toPath()

        testFilePaths.forEach { testFile ->
            if (!testFile.name.endsWith("Spec.kt")) {
                logger.warn("Warning: file ${testFile} does not match the expected file name pattern. It does not end with 'Spec.kt'.")
            } else {
                def relativeTestFilePath = testRoot.relativize(testFile.toPath())
                def testFileName = relativeTestFilePath.fileName.toString()
                def mainFileName = testFileName.replaceAll("Spec\\.kt\$", ".kt")
                def relativeMainFilePath = relativeTestFilePath.parent.resolve(mainFileName)
                def expectedMainFilePath = mainRoot.resolve(relativeMainFilePath)

                if (!expectedMainFilePath.toFile().exists()) {
                    logger.warn("Warning: expected test file ${testFile} to have a corresponding main file ${expectedMainFilePath}, but it does not exist.")
                }
            }
        }

        upToDateFile.get().text = System.currentTimeMillis()
    }
}
