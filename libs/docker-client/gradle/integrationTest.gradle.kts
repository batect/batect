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

val sourceSets = the<SourceSetContainer>()
val mainAndTestCommon = sourceSets["main"].output + sourceSets["testCommon"].output

val integrationTest by sourceSets.creating {
    compileClasspath += mainAndTestCommon
    runtimeClasspath += mainAndTestCommon
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests."
    group = "Verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath

    systemProperty("skipBuildKitTests", "skipBuildKitTests" in project.properties)

    mustRunAfter("test")

    outputs.upToDateWhen { false }
}

tasks.named("check") {
    dependsOn("integrationTestClasses")
}

tasks.named("integrationTestClasses") {
    mustRunAfter("test")
}

dependencies {
    integrationTestImplementation(libs.osplatformfinder)
}
