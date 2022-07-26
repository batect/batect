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
    alias(libs.plugins.wire)
    id("batect-kotlin")
    id("de.undercouch.download")
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jnr.posix)
    implementation("com.squareup.okhttp3:okhttp")
    implementation(libs.commons.compress)
    implementation(libs.bouncycastle)
    implementation(libs.wire.grpc.client)

    implementation(project(":libs:logging"))
    implementation(project(":libs:os"))
    implementation(project(":libs:sockets"))
    implementation(project(":libs:telemetry"))
    implementation(project(":libs:primitives"))

    testImplementation(libs.jimfs)
    testImplementation(libs.hamkrestjson)

    testImplementation(project(":libs:logging-test-utils"))
    testImplementation(project(":libs:test-utils"))
}

checkUnitTestLayout {
    ignoreFileNameCheck.set(
        fileTree("src/unitTest/kotlin") {
            include("batect/docker/api/Assertions.kt")
        }
    )
}

apply(from = "gradle/protobuf.gradle")
