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
    id("com.diffplug.spotless")
    alias(libs.plugins.reckon)
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
}

repositories {
    mavenCentral()
}

apply(from = "$rootDir/gradle/spotless.gradle.kts")
apply(from = "$rootDir/gradle/updateInfoUpload.gradle.kts")
apply(from = "$rootDir/gradle/utilities.gradle")
apply(from = "$rootDir/gradle/versioning.gradle")

tasks {
    register<Sync>("assembleRelease") {
        description = "Prepares files for release."
        group = "Distribution"

        from(project("app").getTasksByName("shadowJar", false))
        from(project("wrapper").getTasksByName("build", false))
        into(buildDir.resolve("release"))
    }

    withType<Wrapper> {
        distributionType = Wrapper.DistributionType.ALL
    }
}
