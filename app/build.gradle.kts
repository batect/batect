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

val okhttpVersion: String by project
val kotlinxSerializationVersion: String by project
val kodeinVersion: String by project
val okioVersion: String by project
val jnrPosixVersion: String by project
val kamlVersion: String by project
val hypirionIoVersion: String by project
val bouncycastleVersion: String by project
val osPlatformFinderVersion: String by project
val hamkrestJsonVersion: String by project
val jimfsVersion: String by project

plugins {
    id("com.github.johnrengelman.shadow")
    id("batect-kotlin")
    application
}

base {
    archivesBaseName = "batect"
}

application {
    applicationName = "batect"
    mainClassName = "batect.ApplicationKt"
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:$okhttpVersion"))
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("com.squareup.okio:okio:$okioVersion")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.github.jnr:jnr-posix:$jnrPosixVersion")
    implementation("com.charleskorn.kaml:kaml:$kamlVersion")
    implementation("com.hypirion:io:$hypirionIoVersion")
    implementation("org.bouncycastle:bcpkix-jdk15on:$bouncycastleVersion")
    implementation("org.graylog.repackaged:os-platform-finder:$osPlatformFinderVersion")

    implementation(project(":libs:docker-client"))
    implementation(project(":libs:git-client"))
    implementation(project(":libs:io"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:primitives"))
    implementation(project(":libs:os"))
    implementation(project(":libs:telemetry"))

    testImplementation("org.araqnid.hamkrest:hamkrest-json:$hamkrestJsonVersion")
    testImplementation("com.google.jimfs:jimfs:$jimfsVersion")
    testImplementation(project(":libs:test-utils"))
    testImplementation(project(":libs:logging-test-utils"))
}

tasks.named("check").configure {
    dependsOn(":libs:check")
}

checkUnitTestLayout {
    ignoreFileNameCheck.set(
        fileTree("src/unitTest/kotlin") {
            include(
                "batect/cli/commands/FakeDockerConnectivity.kt",
                "batect/testutils/**",
                "batect/config/ExceptionMatchers.kt"
            )
        }
    )

    ignoreMissingMainFile.set(
        fileTree("src/unitTest/kotlin") {
            include(
                "batect/execution/model/steps/*.kt",
                "batect/execution/model/events/*.kt"
            )
        }
    )
}

apply {
    from("gradle/completionTest.gradle")
    from("gradle/journeyTest.gradle")
    from("gradle/profiling.gradle")
    from("gradle/shadow.gradle")
    from("gradle/versionInfo.gradle")
}
