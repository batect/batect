/*
    Copyright 2017-2022 Charles Korn.

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

plugins {
    alias(libs.plugins.shadow)
    id("batect-kotlin")
    application
}

base {
    archivesName.set("batect")
}

application {
    applicationName = "batect"
    mainClass.set("batect.ApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(platform(libs.okio.bom))
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kodein)
    implementation("com.squareup.okio:okio")
    implementation("com.squareup.okhttp3:okhttp")
    implementation(libs.jnr.posix)
    implementation(libs.kaml)
    implementation(libs.bouncycastle)
    implementation(libs.osplatformfinder)
    implementation(libs.okhttp.systemkeystore)
    implementation(libs.batect.dockerclient)

    implementation(project(":libs:git-client"))
    implementation(project(":libs:io"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:primitives"))
    implementation(project(":libs:os"))
    implementation(project(":libs:telemetry"))

    testImplementation(libs.hamkrestjson)
    testImplementation(libs.jimfs)
    testImplementation(project(":libs:test-utils"))
    testImplementation(project(":libs:logging-test-utils"))
}

tasks.named("check").configure {
    dependsOn(":libs:check")
    dependsOn(":spotlessCheck")
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
    from("gradle/completionTest.gradle.kts")
    from("gradle/journeyTest.gradle")
    from("gradle/shadow.gradle")
    from("gradle/versionInfo.gradle")
}
