package batect.buildtools.performance

import java.nio.file.Files
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

class PerformanceTestReport extends DefaultTask {
    @InputDirectory
    File testResultsDirectory = project.performanceTestScenarioResultsDirectory.toFile()

    @OutputFile
    File reportFile = project.performanceTestResultsDirectory.resolve("report.txt").toFile()

    @TaskAction
    def run() {
        def scenarioResultFiles = testResultsDirectory.listFiles()
        def lines = scenarioResultFiles.collect { getSummary(it) }

        Files.write(reportFile.toPath(), lines)

        lines.forEach { println(it) }
    }

    String getSummary(File resultFile) {
        def lines = Files.readAllLines(resultFile.toPath())
        def name = getDescription(lines)
        def times = getTimes(lines)
        def count = times.size()
        def min = times.min()
        def max = times.max()
        def average = Math.round(times.sum() / count)

        return "$name: min=$min, avg=$average, max=$max from $count iterations"
    }

    private String getDescription(List<String> lines) {
        return lines.find { it.startsWith("Description: ") }
                .substring("Description: ".length())
    }

    private List<Integer> getTimes(List<String> lines) {
        return lines.findAll { it.startsWith("Time: ") }
                .collect { Integer.parseInt(it.substring("Time: ".length())) }
    }
}
