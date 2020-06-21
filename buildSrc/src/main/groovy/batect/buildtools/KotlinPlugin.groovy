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

package batect.buildtools

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.repositories {
            mavenCentral()
        }

        applyKotlin(project)
        applySpotless(project)
    }

    private void applyKotlin(Project project) {
        project.plugins.apply('org.jetbrains.kotlin.jvm')
        project.plugins.apply('org.jetbrains.kotlin.plugin.serialization')

        project.sourceCompatibility = JavaVersion.VERSION_1_8
        project.targetCompatibility = JavaVersion.VERSION_1_8

        project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = ["-progressive"]
            }
        }
    }

    private void applySpotless(Project project) {
        project.plugins.apply('com.diffplug.gradle.spotless')

        def kotlinLicenseHeader = "/*${project.rootProject.licenseText}*/\n\n"
        project.extensions.add("kotlinLicenseHeader", kotlinLicenseHeader)

        project.spotless {
            kotlin {
                ktlint("0.30.0")

                licenseHeader kotlinLicenseHeader

                trimTrailingWhitespace()
                indentWithSpaces()
                endWithNewline()
            }
        }

        project.afterEvaluate {
            project.tasks.named('spotlessKotlinCheck') {
                mustRunAfter 'test'
            }
        }
    }
}
