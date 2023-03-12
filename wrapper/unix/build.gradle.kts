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
import java.io.ByteArrayOutputStream

val shellcheckVersion: String by project

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.commons.codec)
    }
}

val templateFilePath = "src/template.sh"
val templateFile = file(templateFilePath)

tasks.register<Exec>("shellcheck") {
    description = "Runs the shellcheck linter on the wrapper script template."
    group = "Verification"

    val upToDateFile = temporaryDir.resolve("lastRun")

    inputs.file(templateFile)
    outputs.file(upToDateFile)

    commandLine(
        "docker", "run", "--rm", "-t",
        "-v", "$projectDir:/code",
        "-w", "/code",
        "koalaman/shellcheck:$shellcheckVersion", templateFilePath,
    )

    doLast {
        // Based on https://stackoverflow.com/questions/1406473/simulate-touch-command-with-java
        upToDateFile.writeText(System.currentTimeMillis().toString())
    }
}

tasks.register<Copy>("copyTestJar") {
    description = "Copies the test application JAR into place for use during the wrapper script tests."
    group = "Build"

    from(project(":wrapper:testapp").getTasksByName("jar", false))
    into("test")
}

val testEnvironmentImageName = "batect-wrapper-test-env"

tasks.register<Exec>("buildTestEnvironmentImage") {
    description = "Build the Docker image used as a test environment."
    group = "Verification"

    val imageDirectory = file("test-env")
    inputs.dir(imageDirectory)

    standardOutput = ByteArrayOutputStream()
    errorOutput = standardOutput
    isIgnoreExitValue = true
    workingDir = imageDirectory

    commandLine(
        "docker",
        "build",
        "-t",
        testEnvironmentImageName,
        imageDirectory.path,
    )

    doLast {
        if (executionResult.get().exitValue != 0) {
            throw GradleException("Building image failed: $standardOutput")
        }
    }
}

tasks.register("build") {
    description = "Build the wrapper script."
    group = "Build"

    val scriptDir = buildDir.resolve("scripts")
    val scriptFile = scriptDir.resolve(rootProject.name)
    val shadowJarTask = project(":app").getTasksByName("shadowJar", false).first()

    inputs.file(templateFile)
    inputs.files(shadowJarTask)
    inputs.property("version") { version.toString() }
    outputs.file(scriptFile)

    dependsOn(":app:jar")

    doLast {
        val hash = DigestUtils.sha256Hex(shadowJarTask.outputs.files.singleFile.readBytes())

        val script = templateFile.readText()
            .replace("VERSION-GOES-HERE", version.toString())
            .replace("CHECKSUM-GOES-HERE", hash)

        scriptDir.mkdirs()
        scriptFile.writeText(script)
    }
}

tasks.register<Exec>("test") {
    description = "Runs the unit tests."
    group = "Verification"

    val upToDateFile = temporaryDir.resolve("lastRun")

    inputs.files(fileTree("test") { include("**/*") })
    inputs.files(tasks["copyTestJar"])
    inputs.files(templateFile)
    outputs.file(upToDateFile)

    dependsOn("buildTestEnvironmentImage")

    commandLine(
        "docker", "run", "--rm", "-t",
        "-v", "$projectDir:/code",
        "-w", "/code",
        testEnvironmentImageName, "./test/tests.py",
    )

    doLast {
        // Based on https://stackoverflow.com/questions/1406473/simulate-touch-command-with-java
        upToDateFile.writeText(System.currentTimeMillis().toString())
    }
}

tasks.register("check") {
    description = "Runs all checks."
    group = "Verification"

    dependsOn("shellcheck")
    dependsOn("test")
}
