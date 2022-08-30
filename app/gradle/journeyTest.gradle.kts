/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

import batect.buildtools.JourneyTestNamingCheckTask

val sourceSets = the<SourceSetContainer>()

val journeyTest by sourceSets.creating {
    compileClasspath += sourceSets["testCommon"].output
    runtimeClasspath += sourceSets["testCommon"].output
}

configurations["journeyTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["journeyTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    "journeyTestImplementation"(libs.kotest.assertions.core)
}

abstract class JourneyTest : Test()

tasks.withType<JourneyTest>().configureEach {
    group = "Verification"
    testClassesDirs = journeyTest.output.classesDirs
    classpath = journeyTest.runtimeClasspath

    dependsOn(tasks["journeyTestClasses"])
    dependsOn(tasks["installShadowDist"])

    inputs.files(tasks["installShadowDist"])

    environment("BATECT_ENABLE_TELEMETRY", "false")
}

val windowsTests = "batect/journeytests/windowscontainers/**"
val cleanupTests = "batect/journeytests/cleanup/**"

val journeyTestWindowsContainers by tasks.registering(JourneyTest::class) {
    description = "Runs the journey tests for Windows containers."

    include(windowsTests)

    onlyIf { "windowsContainers" in project.properties }
}

val journeyTestLinuxContainersParallelisable by tasks.registering(JourneyTest::class) {
    description = "Runs the journey tests for Linux containers that can run in parallel."

    exclude(windowsTests)
    exclude(cleanupTests)

    onlyIf { "windowsContainers" !in project.properties }

    maxParallelForks = 4
}

val journeyTestLinuxContainersNonParallelisable by tasks.registering(JourneyTest::class) {
    description = "Runs the journey tests for Linux containers that cannot run in parallel."

    include(cleanupTests)

    onlyIf { "windowsContainers" !in project.properties }
}

// This task is intended to be a lifecycle task (a shortcut to running the other Linux journey test tasks). It doesn't
// run any tests itself, and so shouldn't be a JourneyTest.
val journeyTestLinuxContainers by tasks.registering {
    description = "Runs the journey tests for Linux containers."
    group = "Verification"

    dependsOn(journeyTestLinuxContainersParallelisable)
    dependsOn(journeyTestLinuxContainersNonParallelisable)
}

tasks.register("journeyTest") {
    description = "Runs the journey tests."
    group = "Verification"

    dependsOn(journeyTestWindowsContainers)
    dependsOn(journeyTestLinuxContainers)
}

afterEvaluate {
    tasks.named<JourneyTestNamingCheckTask>("checkJourneyTestNaming").configure {
        ignoreFileNameCheck.set(
            fileTree("src/journeyTest/kotlin") {
                include("batect/journeytests/testutils/**")
            }
        )
    }
}
