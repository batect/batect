/*
   Copyright 2017-2020 Charles Korn.

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
    id("java-gradle-plugin")
    id("groovy")
    kotlin("jvm") version "1.5.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(localGroovy())
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-serialization")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:5.14.1")
    implementation("com.google.cloud:google-cloud-storage:1.118.0")
}

gradlePlugin {
    plugins {
        create("batectKotlinPlugins") {
            id = "batect-kotlin"
            implementationClass = "batect.buildtools.KotlinPlugin"
        }
    }
}
