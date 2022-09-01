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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.SimpleTimeZone

val isReleaseBuild: Boolean by rootProject.extra
val licenseText: String by rootProject.extra
val gitCommitHash: String by rootProject.extra
val gitCommitHumanDate: String by rootProject.extra
val simpleVersion: String by rootProject.extra

fun getBuildDate(): String =
    if (isReleaseBuild) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")
        dateFormat.setTimeZone(SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"))
        dateFormat.format(Date())
    } else {
        "(the build date is not populated for development builds)"
    }

fun versionInfoFileContents(): String {
    val contents = "/*$licenseText*/\n" +
        """

        package batect

        import batect.logging.LogMessageBuilder
        import batect.primitives.ApplicationVersionInfoProvider
        import batect.primitives.Version
        import kotlinx.serialization.Serializable

        // This file is generated automatically by the $generateVersionInfoFile.name Gradle task.
        // Do not edit it (it will be overwritten on the next build anyway).
        // Do not commit it.
        @Serializable
        class VersionInfo : ApplicationVersionInfoProvider {
            val gitCommitHash = "$gitCommitHash"
            val gitCommitDate = "$gitCommitHumanDate"
            override val version = Version.parse("$simpleVersion")
            val buildDate = "${getBuildDate()}"
        }

        fun LogMessageBuilder.data(key: String, value: VersionInfo) = this.data(key, value, VersionInfo.serializer())

        """.trimIndent()

    return contents.replace("\n", System.lineSeparator())
}

val versionInfoFile = file("src/main/kotlin/batect/VersionInfo.kt")

val generateVersionInfoFile by tasks.registering {
    description = "Generates the version info file that is compiled into the application."
    group = "Build"

    inputs.property("version") { simpleVersion }
    inputs.property("gitCommitHash") { gitCommitHash }
    inputs.property("gitCommitHumanDate") { gitCommitHumanDate }
    inputs.property("buildDate") { getBuildDate() }
    outputs.file(versionInfoFile)

    doLast {
        versionInfoFile.writeText(versionInfoFileContents())
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateVersionInfoFile)
}

val cleanVersionInfo by tasks.registering(Delete::class) {
    description = "Removes the generated version info file."
    group = "Other"

    delete(versionInfoFile)
}

tasks.named("clean").configure {
    dependsOn(cleanVersionInfo)
}
