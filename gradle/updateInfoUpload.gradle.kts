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

import batect.buildtools.GoogleCloudStorageUpload
import java.nio.file.Files

val updateInfoFile = buildDir.resolve("updateInfo").resolve("latest.json")

val generateUpdateInfoFile = tasks.register("generateUpdateInfoFile") {
    description = "Generates update information file."
    group = "Distribution"

    inputs.property("version", { project.version.toString() })
    outputs.file(updateInfoFile)

    doLast {
        val version = project.version.toString()
        val content = """
            {
              "url": "https://github.com/batect/batect/releases/tag/$version",
              "version": "$version",
              "files": [
                {
                  "name": "batect",
                  "url": "https://github.com/batect/batect/releases/download/$version/batect"
                },
                {
                  "name": "batect.cmd",
                  "url": "https://github.com/batect/batect/releases/download/$version/batect.cmd"
                }
              ]
            }
        """
            .replace(" ", "")
            .replace("\n", "")

        Files.write(updateInfoFile.toPath(), content.toByteArray(Charsets.UTF_8))
    }
}

tasks.register<GoogleCloudStorageUpload>("uploadUpdateInfoFile") {
    description = "Uploads update information file to publicly-accessible location."
    group = "Distribution"

    dependsOn(generateUpdateInfoFile)

    sourceFile.set(updateInfoFile)
    projectId.set("batect-updates-prod")
    bucket.set("batect-updates-prod-public")
    destinationPath.set("v1/latest.json")
}
