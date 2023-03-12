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
import org.apache.tools.ant.taskdefs.condition.Os

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.commons.codec)
    }
}

val powershellTemplateFile = file("src/template.ps1")
val batchTemplateFile = file("src/template.cmd")

tasks.register("build") {
    description = "Build the wrapper script."
    group = "Build"

    val scriptDir = buildDir.resolve("scripts")
    val scriptFile = scriptDir.resolve(rootProject.name + ".cmd")
    val shadowJarTask = project(":app").getTasksByName("shadowJar", false).first()

    inputs.file(powershellTemplateFile)
    inputs.file(batchTemplateFile)
    inputs.files(shadowJarTask)
    inputs.property("version") { version.toString() }
    outputs.file(scriptFile)

    dependsOn(":app:jar")

    doLast {
        val hash = DigestUtils.sha256Hex(shadowJarTask.outputs.files.singleFile.readBytes())

        val powershellScript = powershellTemplateFile.readText()
            .replace("VERSION-GOES-HERE", version.toString())
            .replace("CHECKSUM-GOES-HERE", hash)

        // Why the double new line in join() below? ^ at the end of a line followed by a non-empty line produces a space,
        // whereas ^ followed by an empty line produces a single new line character in the resulting string.
        val escapedPowershellScript = powershellScript
            .replace("|", "^|")
            .replace("%", "%%")
            .lines()
            .joinToString("^\r\n\r\n")

        scriptDir.mkdirs()
        scriptFile.writeText(
            batchTemplateFile.readText()
                .replace("POWERSHELL-SCRIPT-GOES-HERE", escapedPowershellScript)
                .replace("VERSION-GOES-HERE", version.toString()),
        )
    }
}

tasks.register<Copy>("copyTestJar") {
    description = "Copies the test application JAR into place for use during the wrapper script tests."
    group = "Build"

    from(project(":wrapper:testapp").getTasksByName("jar", false))
    into("test")
}

tasks.register<Exec>("test") {
    description = "Runs the unit tests."
    group = "Verification"

    val upToDateFile = temporaryDir.resolve("lastRun")

    inputs.files(tasks["build"])
    inputs.files(tasks["copyTestJar"])
    inputs.files(fileTree("test") { include("**/*") })
    inputs.property("version") { version.toString() }
    outputs.file(upToDateFile)

    val executableName = if (Os.isFamily(Os.FAMILY_WINDOWS)) "python" else "python3"

    commandLine(executableName, "./test/tests.py")
    environment("BATECT_VERSION", version.toString())

    doLast {
        // Based on https://stackoverflow.com/questions/1406473/simulate-touch-command-with-java
        upToDateFile.writeText(System.currentTimeMillis().toString())
    }
}

tasks.register("check") {
    description = "Runs all checks."
    group = "Verification"

    dependsOn("test")
    dependsOn("build")
}
