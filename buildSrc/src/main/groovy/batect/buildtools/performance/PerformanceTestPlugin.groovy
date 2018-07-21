package batect.buildtools.performance

import org.gradle.api.Plugin
import org.gradle.api.Project

class PerformanceTestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        PerformanceTestExtension extension = project.extensions.create("performanceTest", PerformanceTestExtension.class, project)

        def performanceTestScenarios = project.tasks.create("performanceTestScenarios")

        def performanceTestReport = project.tasks.create("performanceTestReport", PerformanceTestReport.class) {
            it.testScenarioResultsDirectory.set(extension.testScenarioResultsDirectory)
            it.testResultsDirectory.set(extension.testResultsDirectory)

            it.mustRunAfter(performanceTestScenarios)
        }

        project.tasks.create("performanceTest") {
            it.dependsOn(performanceTestScenarios)
            it.dependsOn(performanceTestReport)
        }

        project.tasks.withType(PerformanceTestScenario.class) {
            it.outputDirectory = extension.testScenarioResultsDirectory

            performanceTestScenarios.dependsOn(it)
            performanceTestReport.mustRunAfter(it)
        }
    }
}
