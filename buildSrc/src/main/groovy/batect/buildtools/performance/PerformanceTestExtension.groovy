package batect.buildtools.performance

import org.gradle.api.Project
import org.gradle.api.provider.Property

class PerformanceTestExtension {
    Property<File> testResultsDirectory
    Property<File> testScenarioResultsDirectory

    PerformanceTestExtension(Project project) {
        testResultsDirectory = project.objects.property(File.class)
        testScenarioResultsDirectory = project.objects.property(File.class)

        def reportsPath = project.buildDir.toPath().resolve("reports/performanceTests")
        testResultsDirectory.set(reportsPath.toFile())
        testScenarioResultsDirectory.set(reportsPath.resolve("scenarios").toFile())
    }
}
