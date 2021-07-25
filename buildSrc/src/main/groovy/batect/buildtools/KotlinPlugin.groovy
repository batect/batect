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

package batect.buildtools

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.repositories {
            mavenCentral()
            jcenter()
        }

        applyKotlin(project)
        applySpotless(project)
        configureTesting(project)
        applyJacoco(project)
        configureJar(project)
        applyLicensee(project)
    }

    private void applyKotlin(Project project) {
        project.plugins.apply('org.jetbrains.kotlin.jvm')
        project.plugins.apply('org.jetbrains.kotlin.plugin.serialization')

        project.dependencies {
            implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
        }

        project.sourceCompatibility = JavaVersion.VERSION_1_8
        project.targetCompatibility = JavaVersion.VERSION_1_8

        project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = ["-progressive", "-Xopt-in=kotlin.RequiresOptIn"]
            }
        }
    }

    private void applySpotless(Project project) {
        project.plugins.apply('com.diffplug.spotless')

        def kotlinLicenseHeader = "/*${project.rootProject.licenseText}*/\n\n"
        project.extensions.add("kotlinLicenseHeader", kotlinLicenseHeader)

        project.spotless {
            kotlin {
                ktlint("0.41.0")

                licenseHeader kotlinLicenseHeader

                trimTrailingWhitespace()
                indentWithSpaces()
                endWithNewline()

                targetExclude('build/**')
                targetExclude('src/generated/**')
            }
        }

        project.afterEvaluate {
            project.tasks.named('spotlessKotlinCheck') {
                mustRunAfter 'test'
            }

            project.tasks.named('spotlessKotlin') {
                mustRunAfter 'test'
            }
        }
    }

    private void configureTesting(Project project) {
        configureCommonTesting(project)
        configureUnitTesting(project)
        configureUnitTestLayoutCheck(project)
        configureJourneyTestNameCheck(project)
    }

    private void configureCommonTesting(Project project) {
        project.sourceSets {
            testCommon
        }

        project.dependencies {
            testCommonImplementation "com.natpryce:hamkrest:${project.property("hamkrestVersion")}"
            testCommonImplementation "org.spekframework.spek2:spek-dsl-jvm:${project.property("spekVersion")}"
            testCommonRuntimeOnly "org.spekframework.spek2:spek-runner-junit5:${project.property("spekVersion")}"
            testCommonRuntimeOnly "org.junit.platform:junit-platform-engine:${project.property("junitPlatformVersion")}"
        }

        project.tasks.withType(Test).configureEach {
            if (JavaVersion.current() >= JavaVersion.VERSION_1_9) {
                jvmArgs '-Xshare:off', '--add-opens', 'java.base/sun.nio.ch=ALL-UNNAMED', '--add-opens', 'java.base/java.io=ALL-UNNAMED'
            } else {
                jvmArgs '-Xshare:off'
            }

            useJUnitPlatform {
                includeEngines 'spek2'
            }

            testLogging {
                events "failed"
                events "skipped"
                events "standard_out"
                events "standard_error"

                showExceptions true
                showStackTraces true
                showCauses true
                exceptionFormat "full"
            }
        }
    }

    private void configureUnitTesting(Project project) {
        project.sourceSets {
            test {
                kotlin {
                    srcDirs = ['src/unitTest/kotlin']
                }

                resources {
                    srcDirs = ['src/unitTest/resources']
                }

                compileClasspath += testCommon.output
                runtimeClasspath += testCommon.output
            }
        }

        project.configurations {
            testImplementation.extendsFrom testCommonImplementation
            testRuntimeOnly.extendsFrom testCommonRuntimeOnly
        }

        project.dependencies {
            // We don't use mockito directly, but mockito-kotlin does refer to it, so override it to get the latest version.
            testImplementation "org.mockito:mockito-core:${project.property("mockitoCoreVersion")}"
            testImplementation "org.mockito.kotlin:mockito-kotlin:${project.property("mockitoKotlinVersion")}"
        }
    }

    private void configureUnitTestLayoutCheck(Project project) {
        def checkUnitTestLayoutExtension = project.extensions.create("checkUnitTestLayout", UnitTestLayoutCheckExtension.class, project)

        def checkUnitTestLayoutTask = project.tasks.register('checkUnitTestLayout', UnitTestLayoutCheckTask.class) {
            mustRunAfter 'test'

            ignoreFileNameCheck.set(checkUnitTestLayoutExtension.ignoreFileNameCheck)
            ignoreMissingMainFile.set(checkUnitTestLayoutExtension.ignoreMissingMainFile)
        }

        project.tasks.named('check') {
            dependsOn checkUnitTestLayoutTask
        }
    }

    private void configureJourneyTestNameCheck(Project project) {
        def checkJourneyTestNamingExtension = project.extensions.create("checkJourneyTestNaming", JourneyTestNamingCheckExtension.class, project)

        project.afterEvaluate {
            def kotlin = project.extensions.getByType(KotlinJvmProjectExtension)

            if (!kotlin.sourceSets.names.contains('journeyTest')) {
                return
            }

            def checkJourneyTestNamingTask = project.tasks.register('checkJourneyTestNaming', JourneyTestNamingCheckTask.class) {
                mustRunAfter 'test'

                files.set(kotlin.sourceSets.named('journeyTest').get().kotlin)
                ignoreFileNameCheck.set(checkJourneyTestNamingExtension.ignoreFileNameCheck)
            }

            project.tasks.named('check') {
                dependsOn checkJourneyTestNamingTask
            }
        }
    }

    private void applyJacoco(Project project) {
        project.plugins.apply('jacoco')

        project.jacoco {
            toolVersion = '0.8.7'
        }

        project.jacocoTestReport {
            reports {
                xml.enabled true
            }
        }

        def generateCoverage = System.getProperty("generateCoverage", "false")

        project.tasks.withType(Test).configureEach { task ->
            task.jacoco {
                enabled = generateCoverage == "true" && task.name == "test"
            }
        }
    }

    private void configureJar(Project project) {
        project.tasks.withType(Jar).configureEach { task ->
            def version = project.version.toString()
            def delimiter = "-dev"
            def delimiterIndex = version.indexOf(delimiter)

            if (delimiterIndex != -1) {
                version = version.substring(0, delimiterIndex + delimiter.length())
            }

            task.archiveVersion.convention(version)
        }
    }

    private void applyLicensee(Project project) {
        project.plugins.apply('app.cash.licensee')

        project.licensee {
            allow('Apache-2.0')
            allowUrl('http://opensource.org/licenses/Apache-2.0')

            allow('MIT')
            allowUrl('http://opensource.org/licenses/MIT')
            allowUrl('http://www.opensource.org/licenses/mit-license.php')
            allowUrl('https://github.com/mockito/mockito/blob/release/3.x/LICENSE') // MIT

            allowUrl('https://github.com/spekframework/spek/blob/2.x/LICENSE.TXT') // BSD
            allowUrl('https://asm.ow2.io/license.html') // BSD

            allow('EPL-1.0')

            allowDependency('com.github.jnr', 'jnr-posix', '3.1.7') {
                because 'Licensed under three licenses, including EPL 2.0 and LGPL'
            }

            allowDependency('org.mockito.kotlin', 'mockito-kotlin', '3.2.0') {
                because 'Licensee incorrectly fails this dependency due to https://github.com/cashapp/licensee/issues/40'
            }

            allowDependency('org.checkerframework', 'checker-compat-qual', '2.5.5') {
                because 'https://github.com/typetools/checker-framework/blob/checker-framework-2.5.5/LICENSE.txt says that the annotations are licensed under the MIT license'
            }

            allowDependency('org.bouncycastle', 'bcutil-jdk15on', '1.69') {
                because 'https://www.bouncycastle.org/licence.html is the MIT license'
            }

            allowDependency('org.bouncycastle', 'bcpkix-jdk15on', '1.69') {
                because 'https://www.bouncycastle.org/licence.html is the MIT license'
            }

            allowDependency('org.bouncycastle', 'bcprov-jdk15on', '1.69') {
                because 'https://www.bouncycastle.org/licence.html is the MIT license'
            }
        }
    }
}
