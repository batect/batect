/*
   Copyright 2017-2018 Charles Korn.

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

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import oshi.SystemInfo
import oshi.hardware.HardwareAbstractionLayer

import java.nio.file.Files
import java.time.Duration

class PerformanceTestScenario extends DefaultTask {
    @Input
    String description

    @Input
    int count = 5

    @InputFile
    Property<File> executable

    @Input
    List<String> args

    @InputDirectory
    String workingDirectory = "."

    @Input
    Property<File> outputDirectory

    @OutputFile
    Property<File> outputFile

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
        def durations = (1..count).collect {
            println(">> Running iteration $it")

            runProcess()
        }

        writeReport(durations)
    }

    Duration runProcess() {
        def resolvedWorkingDirectory = resolveWorkingDirectory()

        def startTime = System.nanoTime()

        project.exec {
            executable this.executable.get()
            args this.args
            workingDir resolvedWorkingDirectory
            setIgnoreExitValue false
        }

        def endTime = System.nanoTime()
        def elapsedTime = Duration.ofNanos(endTime - startTime)

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
