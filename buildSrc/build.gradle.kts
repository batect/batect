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

import org.gradle.api.JavaVersion

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.6.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-serialization")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.2.2")
    implementation("com.google.cloud:google-cloud-storage:2.4.1")
    implementation("app.cash.licensee:licensee-gradle-plugin:1.3.1")
}

gradlePlugin {
    plugins {
        create("batectKotlinPlugins") {
            id = "batect-kotlin"
            implementationClass = "batect.buildtools.KotlinPlugin"
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
