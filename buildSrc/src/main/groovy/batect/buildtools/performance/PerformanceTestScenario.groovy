package batect.buildtools.performance

import java.nio.file.Files
import java.time.Duration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import oshi.SystemInfo
import oshi.hardware.HardwareAbstractionLayer

class PerformanceTestScenario extends DefaultTask {
    @Input
    String description

    @Input
    int count = 5

    @Input
    List<String> args

    @InputDirectory
    String workingDirectory

    @OutputFile
    File outputFile = project.performanceTestScenarioResultsDirectory.resolve(name + ".perf").toFile()

    PerformanceTestScenario() {
        this.dependsOn("installShadowDist")
        project.tasks.maybeCreate("performanceTestScenarios").dependsOn(this)
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
        def resolvedExecutablePath = resolveExecutablePath()
        def resolvedWorkingDirectory = resolveWorkingDirectory()

        def startTime = System.nanoTime()

        project.exec {
            args this.args
            executable resolvedExecutablePath
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

    private String resolveExecutablePath() {
        def outputs = project.tasks.getByName("installShadowDist").outputs
        def outputDir = outputs.files.singleFile

        return "$outputDir/bin/batect"
    }

    private def writeReport(List<Duration> durations) {
        SystemInfo si = new SystemInfo()
        HardwareAbstractionLayer hal = si.getHardware()

        String gitCommit = project.getCurrentGitCommit()
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

        Files.write(outputFile.toPath(), lines)
    }
}
