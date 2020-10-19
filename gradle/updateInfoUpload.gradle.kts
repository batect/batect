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

import java.nio.file.Files

tasks.register("generateUpdateInfoFile") {
    description = "Generates update information file."
    group = "Distribution"

    inputs.property("version", { project.version.toString() })

    val outputFile = buildDir.resolve("updateInfo").resolve("latest.json")
    outputs.file(outputFile)

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

        Files.write(outputFile.toPath(), content.toByteArray(Charsets.UTF_8))
    }
}
