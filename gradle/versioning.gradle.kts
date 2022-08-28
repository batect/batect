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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import java.text.SimpleDateFormat
import java.util.Locale

buildscript {
    dependencies {
        classpath(libs.jgit)
    }

    repositories {
        mavenCentral()
    }
}

val validateVersionMatchesTag by tasks.registering {
    description = "Validates that the version currently configured in Gradle matches the current Git commit tag."
    group = "Verification"

    val gitNonExactMatchSuffix = Regex(".*-g[a-f\\d]+$")

    doLast {
        val gitTag = Git.open(rootDir).use { git ->
            val description = git.describe().call()

            // Simulate the "--dirty" option with JGit.
            description.takeUnless { git.status().call().hasUncommittedChanges() } ?: "$description-dirty"
        }

        if ("-dirty" in gitTag) {
            throw GradleException("The working copy is dirty.")
        }

        if (gitTag.matches(gitNonExactMatchSuffix)) {
            throw GradleException("There is no exactly matching tag for this commit, but only '$gitTag'.")
        }

        if (gitTag != project.version.toString()) {
            throw GradleException("The tag for this commit is '$gitTag', but the project version is set to '${project.version}'.")
        }
    }
}

val validateIsReleaseBuild by tasks.registering {
    description = "Validates that the current build is a release build."
    group = "Verification"

    doLast {
        if (!isReleaseBuild()) {
            throw GradleException("The current build is not a release build.")
        }
    }
}

val validateRelease by tasks.registering {
    description = "Validates that the current commit is ready to be released."
    group = "Verification"

    dependsOn(validateVersionMatchesTag)
    dependsOn(validateIsReleaseBuild)
}

fun getCurrentGitCommitHash(): String =
    Git.open(rootDir).use { git ->
        val name = git.repository.resolve(Constants.HEAD).name()

        // Simulate the "--dirty" option with JGit.
        name.takeUnless { git.status().call().hasUncommittedChanges() } ?: "$name-dirty"
    }

fun getCurrentGitCommitHumanDate(): String =
    Git.open(rootDir).use { git ->
        val id = git.repository.resolve(Constants.HEAD)
        val ident = git.repository.parseCommit(id).committerIdent
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).apply { timeZone = ident.timeZone }
        formatter.format(ident.`when`.time)
    }

fun isReleaseBuild(): Boolean = "-" !in project.version.toString()

fun getSimpleVersion(): String = project.version.toString().substringBefore("+")

extra.apply {
    set("gitCommitHash", getCurrentGitCommitHash())
    set("gitCommitHumanDate", getCurrentGitCommitHumanDate())
    set("isReleaseBuild", isReleaseBuild())
    set("simpleVersion", getSimpleVersion())
}
