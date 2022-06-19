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

import com.diffplug.gradle.spotless.SpotlessExtension

val licenseText by extra(
    """
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
"""
)

val kotlinLicenseHeader = "/*$licenseText*/\n\n"
val javaLicenseHeader = "/*$licenseText*/\n\n"
val ktlintVersion = "0.43.2"

configure<SpotlessExtension> {
    format("misc") {
        target(
            fileTree(".") {
                include(
                    "**/*.gradle", "**/*.md", "**/.gitignore", "**/*.yaml", "**/*.yml", "**/*.sh",
                    "**/Dockerfile", "**/*.py", "**/*.json", "**/*.ps1", "**/*.cmd"
                )
                exclude("**/node_modules/*", "**/build/*", "**/.devcontainer/*")
            }
        )

        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }

    format("gradle") {
        target("**/*.gradle")

        // Check what values are really required with: find . -name '*.gradle' -type f -exec sed -n 17p {} \; | cut -d' ' -f1 | sort | uniq
        licenseHeader("/*$licenseText*/\n\n", "(//|apply|def|ext|import|plugins|rootProject|sourceSets|tasks|buildscript|pluginManagement)")
    }

    kotlinGradle {
        target("**/*.gradle.kts")

        ktlint(ktlintVersion)

        // Check what values are really required with: find . -name '*.gradle.kts' -type f -exec sed -n 17p {} \; | cut -d' ' -f1 | sort | uniq
        // TODO: This confuses spotless plugin for settings.gradle.kts -- is
        //       this a spotless issue, or does this configuration need more
        //       specificity?
        licenseHeader(kotlinLicenseHeader, "(val|import|plugins|tasks|//|dependencyResolutionManagement|rootProject)")
    }

    kotlin {
        target("**/*.kt")

        ktlint(ktlintVersion)

        licenseHeader(kotlinLicenseHeader)

        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()

        targetExclude("**/build/**")
        targetExclude("**/src/generated/**")
    }

    java {
        target("**/*.java")
        targetExclude("buildSrc/.gradle/**")

        licenseHeader(javaLicenseHeader)

        removeUnusedImports()
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}

tasks.named("spotlessKotlin") {
    mustRunAfter(project("app").tasks.named("generateVersionInfoFile"))
}

listOf(
    "spotlessGradle",
    "spotlessJava",
    "spotlessKotlin",
    "spotlessKotlinGradle",
    "spotlessMisc",
).forEach {
    tasks.named(it) {
        mustRunAfter(project("libs").project("docker-client").tasks.named("generateMainProtos"))
    }
}
