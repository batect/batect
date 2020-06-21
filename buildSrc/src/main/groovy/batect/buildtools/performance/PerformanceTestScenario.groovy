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

package batect.buildtools.performance

import org.gradle.StartParameter
import org.gradle.api.DefaultTask
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import oshi.SystemInfo
import oshi.hardware.HardwareAbstractionLayer

import java.nio.file.Files
import java.time.Duration

class PerformanceTestScenario extends DefaultTask {
    @Input
    String description

    @Input
    int count = 10

    @Input
    List<String> before = []

    @InputFile
    final Property<File> executable

    @Input
    List<String> args

    @InputDirectory
    String workingDirectory = "."

    @Input
    final Property<File> outputDirectory

    @OutputFile
    final Property<File> outputFile

    PerformanceTestScenario() {
        executable = project.objects.property(File.class)
        outputDirectory = project.objects.property(File.class)
        outputFile = project.objects.property(File.class)

        outputFile.set(project.providers.provider {
            outputDirectory.get().toPath().resolve(name + ".perf").toFile()
        })
    }

    @TaskAction
    def run() {
        ensureNotRunningInDaemon()
        ensurePlainOutputMode()
        runBeforeCommands()

        def durations = (1..count).collect {
            println(">> Running iteration $it")

            runProcess()
        }

        writeReport(durations)
    }

    def ensureNotRunningInDaemon() {
        if (!System.getProperty("sun.java.command").startsWith("org.gradle.wrapper.GradleWrapperMain")) {
            throw new RuntimeException("Performance tests must be run with the Gradle daemon disabled to ensure that output from the application is written directly to the console and not to the daemon's buffered console. Run Gradle with --no-daemon to fix this issue.")
        }
    }

    def ensurePlainOutputMode() {
        if (getServices().get(LoggingConfiguration.class).consoleOutput != ConsoleOutput.Plain) {
            throw new RuntimeException("Performance tests must be run with Gradle's output set to plain mode to ensure that Gradle's output does not interfere with the output from the application. Run Gradle with --console=plain to fix this issue.")
        }
    }

    def runBeforeCommands() {
        def resolvedWorkingDirectory = resolveWorkingDirectory()

        before.each { command ->
            println(">> Running $command")

            def processBuilder = new ProcessBuilder(["sh", "-c", command])
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .directory(resolvedWorkingDirectory)

            def exitCode = processBuilder.start().waitFor()

            if (exitCode != 0) {
                throw new RuntimeException("Command exited with code $exitCode.")
            }
        }
    }

    Duration runProcess() {
        def processBuilder = new ProcessBuilder([this.executable.get().toString()] + this.args)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .directory(resolveWorkingDirectory())

        def startTime = System.nanoTime()
        def exitCode = processBuilder.start().waitFor()
        def endTime = System.nanoTime()

        def elapsedTime = Duration.ofNanos(endTime - startTime)

        if (exitCode != 0) {
            throw new RuntimeException("Command exited with code $exitCode.")
        }

        println(">> Finished in $elapsedTime")
        println()

        return elapsedTime
    }

    private File resolveWorkingDirectory() {
        return project.projectDir.toPath().resolve(workingDirectory).toFile()
    }

    private def writeReport(List<Duration> durations) {
        SystemInfo si = new SystemInfo()
        HardwareAbstractionLayer hal = si.getHardware()

        String gitCommit = project.rootProject.gitCommitHash
        String version = project.version.toString()
        String osDetails = System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
        String jvmDetails = System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version")
        String cpu = hal.getProcessor().toString()

        def lines = [
                "Description: " + description,
                "Git commit: " + gitCommit,
                "Version: " + version,
                "OS: " + osDetails,
                "JVM: " + jvmDetails,
                "CPU: " + cpu
        ] + durations.collect { "Time: " + it.toMillis().toString() }

        Files.write(outputFile.get().toPath(), lines)
    }
}
