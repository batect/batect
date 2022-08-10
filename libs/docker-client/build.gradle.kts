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
    id("batect-kotlin")
}

dependencies {
    implementation(platform(libs.okio.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation("com.squareup.okio:okio")

    implementation(project(":libs:logging"))
    implementation(project(":libs:os"))
    implementation(project(":libs:primitives"))

    testImplementation(project(":libs:test-utils"))
}
