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

import org.apache.commons.codec.digest.DigestUtils

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.commons.codec)
    }
}

evaluationDependsOnChildren()

val sourceTasks = setOf(
    project("unix").tasks.named("build"),
    project("windows").tasks.named("build"),
)

val createChecksumsFile by tasks.registering {
    val sourceFiles = sourceTasks.map { it.map { task -> task.outputs } }

    inputs.files(sourceFiles)

    val outputDir = buildDir.resolve("checksums")
    val outputFile = outputDir.resolve("checksums.sha256")

    outputs.files(outputFile)

    doLast {
        outputDir.mkdirs()

        val content = sourceFiles
            .map { it.get() }
            .flatMap { it.files.files }
            .associate { file -> file.name to sha256HashOf(file) }
            .entries
            .sortedBy { it.key }.joinToString("\n") { "${it.value}  ${it.key}" }

        outputFile.writeText(content, Charsets.UTF_8)
    }
}

fun sha256HashOf(file: File): String {
    return DigestUtils.sha256Hex(file.readBytes())
}

tasks.register<Sync>("build") {
    description = "Build the wrapper scripts."
    group = "Build"

    from(sourceTasks)
    from(createChecksumsFile)
    into(buildDir.resolve("build"))
}
