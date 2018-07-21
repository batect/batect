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
