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

import org.gradle.api.Plugin
import org.gradle.api.Project

class PerformanceTestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        PerformanceTestExtension extension = project.extensions.create("performanceTest", PerformanceTestExtension.class, project)

        project.tasks.withType(PerformanceTestScenario.class) { scenario ->
            scenario.outputDirectory.set(extension.testScenarioResultsDirectory)
        }

        def performanceTestScenarios = project.tasks.register("performanceTestScenarios") {
            it.dependsOn(project.tasks.withType(PerformanceTestScenario.class))
        }

        def performanceTestReport = project.tasks.register("performanceTestReport", PerformanceTestReport.class) {
            it.testScenarioResultsDirectory.set(extension.testScenarioResultsDirectory)
            it.testResultsDirectory.set(extension.testResultsDirectory)

            it.mustRunAfter(project.tasks.withType(PerformanceTestScenario.class))
            it.mustRunAfter(performanceTestScenarios)
        }

        project.tasks.register("performanceTest") {
            it.dependsOn(performanceTestScenarios)
            it.dependsOn(performanceTestReport)
        }
    }
}
