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

package batect.buildtools.performance

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class PerformanceTestReport extends DefaultTask {
    @InputDirectory
    final Property<File> testScenarioResultsDirectory

    // This is just @Input because we only care about the value, not the contents of the directory
    @Input
    final Property<File> testResultsDirectory

    @OutputFile
    final Property<File> reportFile

    PerformanceTestReport() {
        testScenarioResultsDirectory = project.objects.property(File.class)
        testResultsDirectory = project.objects.property(File.class)
        reportFile = project.objects.property(File.class)

        reportFile.set(project.providers.provider {
            testResultsDirectory.get().toPath().resolve("report.txt").toFile()
        })
    }

    @TaskAction
    def run() {
        def scenarioResultFiles = testScenarioResultsDirectory.get().listFiles().sort()
        def lines = scenarioResultFiles.collect { getSummary(it) }

        Files.write(reportFile.get().toPath(), lines)

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
