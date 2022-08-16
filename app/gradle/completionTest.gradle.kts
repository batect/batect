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

import java.io.ByteArrayOutputStream

the<SourceSetContainer>().create("completionTest")

val testEnvironmentImageName = "batect-completion-test-env"
val completionTestDir = file("src/completionTest").absolutePath

tasks.register<Exec>("buildCompletionTestEnvironmentImage") {
    description = "Build the Docker image used as a test environment."
    group = "Verification"

    val imageDirectory = file("$completionTestDir/env")
    inputs.dir(imageDirectory)

    standardOutput = ByteArrayOutputStream()
    errorOutput = standardOutput
    isIgnoreExitValue = true

    environment("DOCKER_BUILDKIT", "1")

    commandLine(
        "docker",
        "build",
        "-t",
        testEnvironmentImageName,
        imageDirectory.path
    )

    doLast {
        if (executionResult.get().exitValue != 0) {
            throw GradleException("Building image failed: $standardOutput")
        }
    }
}

tasks.register<Exec>("completionTest") {
    description = "Runs shell tab completion tests."
    group = "Verification"

    val upToDateFile = temporaryDir.resolve("lastRun")

    inputs.files(fileTree(completionTestDir) { include("**/*") })
    inputs.files(tasks["installShadowDist"])
    outputs.file(upToDateFile)

    dependsOn(tasks["buildCompletionTestEnvironmentImage"])
    dependsOn(tasks["installShadowDist"])

    val args = mutableListOf(
        "docker", "run", "--rm", "-t",
        "-v", "$completionTestDir/tests:/tests",
        "-v", "$buildDir/install/app-shadow:/app",
        "-w", "/tests",
        "-e", "BATECT_ENABLE_TELEMETRY=false",
        testEnvironmentImageName, "./tests.py"
    )

    if (project.hasProperty("focusCompletionTests")) {
        args += "-k"
        args += project.property("focusCompletionTests").toString()
    }

    commandLine = args

    doLast {
        // Based on https://stackoverflow.com/questions/1406473/simulate-touch-command-with-java
        upToDateFile.writeText(System.currentTimeMillis().toString())
    }
}
