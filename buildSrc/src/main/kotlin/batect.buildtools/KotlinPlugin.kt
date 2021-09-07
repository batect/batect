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

import app.cash.licensee.LicenseeExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

class KotlinPlugin : Plugin<Project> {
    @Override
    override fun apply(project: Project) {
        project.repositories {
            mavenCentral()
        }

        applyKotlin(project)
        applySpotless(project)
        configureTesting(project)
        applyJacoco(project)
        configureJar(project)
        applyLicensee(project)
    }

    private fun applyKotlin(project: Project) {
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.kotlin.plugin.serialization")

        project.dependencies {
            add("implementation", platform("org.jetbrains.kotlin:kotlin-bom"))
        }

        project.extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
            it.kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf("-progressive", "-Xopt-in=kotlin.RequiresOptIn")
            }
        }
    }

    private fun applySpotless(project: Project) {
        project.plugins.apply("com.diffplug.spotless")

        val licenseText = project.rootProject.extra.get("licenseText") as String
        val kotlinLicenseHeader = "/*${licenseText}*/\n\n"
        project.extensions.add("kotlinLicenseHeader", kotlinLicenseHeader)

        project.extensions.configure<SpotlessExtension>() {
            kotlin {
                it.ktlint("0.41.0")

                it.licenseHeader(kotlinLicenseHeader)

                it.trimTrailingWhitespace()
                it.indentWithSpaces()
                it.endWithNewline()

                it.targetExclude("build/**")
                it.targetExclude("src/generated/**")
            }
        }

        project.afterEvaluate {
            project.tasks.named<Task>("spotlessKotlinCheck") {
                mustRunAfter("test")
            }

            project.tasks.named<Task>("spotlessKotlin") {
                mustRunAfter("test")
            }
        }
    }

    private fun configureTesting(project: Project) {
        configureCommonTesting(project)
        configureUnitTesting(project)
        configureUnitTestLayoutCheck(project)
        configureJourneyTestNameCheck(project)
    }

    private fun configureCommonTesting(project: Project) {
        project.extensions.configure<SourceSetContainer> {
            create("testCommon")
        }

        project.dependencies {
            add("testCommonImplementation", "com.natpryce:hamkrest:${project.property("hamkrestVersion")}")
            add("testCommonImplementation", "org.spekframework.spek2:spek-dsl-jvm:${project.property("spekVersion")}")
            add("testCommonRuntimeOnly", "org.spekframework.spek2:spek-runner-junit5:${project.property("spekVersion")}")
            add("testCommonRuntimeOnly", "org.junit.platform:junit-platform-engine:${project.property("junitPlatformVersion")}")
        }

        project.tasks.withType<Test>().configureEach {
            if (JavaVersion.current() >= JavaVersion.VERSION_1_9) {
                it.jvmArgs("-Xshare:off", "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
            } else {
                it.jvmArgs("-Xshare:off")
            }

            it.useJUnitPlatform { platform ->
                platform.includeEngines("spek2")
            }

            it.testLogging { logging ->
                logging.events(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
                logging.showExceptions = true
                logging.showStackTraces = true
                logging.showCauses = true
                logging.exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }

    private fun configureUnitTesting(project: Project) {
        project.extensions.configure<KotlinJvmProjectExtension> {
            sourceSets.getByName("test", KotlinSourceSet::class) {
                kotlin.setSrcDirs(listOf("src/unitTest/kotlin"))
            }
        }

        project.extensions.configure<SourceSetContainer> {
            getByName("test") {
                it.resources.setSrcDirs(listOf("src/unitTest/resources"))
            }
        }

        // Do we need to specify further configurations here? There's more than just implementation and runtimeOnly (they're just the only ones we use at the moment)
        project.configurations.getByName<Configuration>("testImplementation") {
            extendsFrom(project.configurations.getByName("testCommonImplementation"))
        }

        project.configurations.getByName<Configuration>("testRuntimeOnly") {
            extendsFrom(project.configurations.getByName("testCommonRuntimeOnly"))
        }

        project.dependencies {
            // We don't use mockito directly, but mockito-kotlin does refer to it, so override it to get the latest version.
            add("testImplementation", "org.mockito:mockito-core:${project.property("mockitoCoreVersion")}")
            add("testImplementation", "org.mockito.kotlin:mockito-kotlin:${project.property("mockitoKotlinVersion")}")
        }
    }

    private fun configureUnitTestLayoutCheck(project: Project) {
        val checkUnitTestLayoutExtension = project.extensions.create<UnitTestLayoutCheckExtension>("checkUnitTestLayout")

        val checkUnitTestLayoutTask = project.tasks.register<UnitTestLayoutCheckTask>("checkUnitTestLayout") {
            mustRunAfter("test")

            ignoreFileNameCheck.set(checkUnitTestLayoutExtension.ignoreFileNameCheck)
            ignoreMissingMainFile.set(checkUnitTestLayoutExtension.ignoreMissingMainFile)
        }

        project.tasks.named<Task>("check") {
            dependsOn(checkUnitTestLayoutTask)
        }
    }

    private fun configureJourneyTestNameCheck(project: Project) {
        val checkJourneyTestNamingExtension = project.extensions.create<JourneyTestNamingCheckExtension>("checkJourneyTestNaming")

        project.afterEvaluate {
            val kotlin = project.extensions.getByType<KotlinJvmProjectExtension>()

            if (!kotlin.sourceSets.names.contains("journeyTest")) {
                return@afterEvaluate
            }

            val checkJourneyTestNamingTask = project.tasks.register<JourneyTestNamingCheckTask>("checkJourneyTestNaming") {
                mustRunAfter("test")

                files.set(kotlin.sourceSets.named("journeyTest").get().kotlin)
                ignoreFileNameCheck.set(checkJourneyTestNamingExtension.ignoreFileNameCheck)
            }

            project.tasks.named<Task>("check") {
                dependsOn(checkJourneyTestNamingTask)
            }
        }
    }

    private fun applyJacoco(project: Project) {
        project.plugins.apply("jacoco")

        project.extensions.configure<JacocoPluginExtension>() {
            toolVersion = "0.8.7"
        }

        project.tasks.withType<JacocoReport>().configureEach { task ->
            task.reports.xml.required.set(true)
        }

        val generateCoverage = System.getProperty("generateCoverage", "false")

        project.tasks.withType<Test>().configureEach { task ->
            task.extensions.configure<JacocoTaskExtension> {
                isEnabled = generateCoverage == "true" && task.name == "test"
            }
        }
    }

    private fun configureJar(project: Project) {
        project.tasks.withType<Jar>().configureEach { task ->
            var version = project.version.toString()
            val delimiter = "-dev"
            val delimiterIndex = version.indexOf(delimiter)

            if (delimiterIndex != -1) {
                version = version.substring(0, delimiterIndex + delimiter.length)
            }

            task.archiveVersion.convention(version)
        }
    }

    private fun applyLicensee(project: Project) {
        project.plugins.apply("app.cash.licensee")

        project.extensions.configure<LicenseeExtension>() {
            allow("Apache-2.0")
            allowUrl("http://opensource.org/licenses/Apache-2.0")

            allow("MIT")
            allowUrl("http://opensource.org/licenses/MIT")
            allowUrl("http://www.opensource.org/licenses/mit-license.php")
            allowUrl("https://github.com/mockito/mockito/blob/release/3.x/LICENSE") // MIT

            allowUrl("https://github.com/spekframework/spek/blob/2.x/LICENSE.TXT") // BSD
            allowUrl("https://asm.ow2.io/license.html") // BSD

            allow("EPL-1.0")
            allowUrl("https://www.eclipse.org/legal/epl-v20.html")

            allowDependency("com.github.jnr", "jnr-posix", "3.1.9") {
                it.because("Licensed under three licenses, including EPL 2.0 and LGPL")
            }

            allowDependency("org.checkerframework", "checker-compat-qual", "2.5.5") {
                it.because("https://github.com/typetools/checker-framework/blob/checker-framework-2.5.5/LICENSE.txt says that the annotations are licensed under the MIT license")
            }

            allowDependency("org.bouncycastle", "bcutil-jdk15on", "1.69") {
                it.because("https://www.bouncycastle.org/licence.html is the MIT license")
            }

            allowDependency("org.bouncycastle", "bcpkix-jdk15on", "1.69") {
                it.because("https://www.bouncycastle.org/licence.html is the MIT license")
            }

            allowDependency("org.bouncycastle", "bcprov-jdk15on", "1.69") {
                it.because("https://www.bouncycastle.org/licence.html is the MIT license")
            }
        }
    }
}
