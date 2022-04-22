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

package batect.telemetry

import batect.os.HostEnvironmentVariables

// This class is based on https://github.com/watson/ci-info.
class CIEnvironmentDetector(
    private val hostEnvironmentVariables: HostEnvironmentVariables
) {
    private val knownSystems = mapOf(
        "AWS CodeBuild" to setOf("CODEBUILD_BUILD_ARN"),
        "AppVeyor" to setOf("APPVEYOR"),
        "Azure Pipelines" to setOf("SYSTEM_TEAMFOUNDATIONCOLLECTIONURI"),
        "Bamboo" to setOf("bamboo_planKey"),
        "Bitbucket Pipelines" to setOf("BITBUCKET_COMMIT"),
        "Bitrise" to setOf("BITRISE_IO"),
        "Buddy" to setOf("BUDDY_WORKSPACE_ID"),
        "Buildkite" to setOf("BUILDKITE"),
        "CircleCI" to setOf("CIRCLECI"),
        "Cirrus CI" to setOf("CIRRUS_CI"),
        "Drone" to setOf("DRONE"),
        "GitHub Actions" to setOf("GITHUB_ACTIONS"),
        "GitLab CI" to setOf("GITLAB_CI"),
        "GoCD" to setOf("GO_PIPELINE_LABEL"),
        "Hudson" to setOf("HUDSON_URL"),
        "Jenkins" to setOf("JENKINS_URL", "BUILD_ID"),
        "Magnum CI" to setOf("MAGNUM"),
        "Netlify CI" to setOf("NETLIFY"),
        "Nevercode" to setOf("NEVERCODE"),
        "Render" to setOf("RENDER"),
        "Sail CI" to setOf("SAILCI"),
        "Semaphore" to setOf("SEMAPHORE"),
        "Shippable" to setOf("SHIPPABLE"),
        "Solano CI" to setOf("TDDIUM"),
        "Strider CD" to setOf("STRIDER"),
        "TaskCluster" to setOf("TASK_ID", "RUN_ID"),
        "TeamCity" to setOf("TEAMCITY_VERSION"),
        "Travis CI" to setOf("TRAVIS"),
        "ZEIT Now" to setOf("NOW_BUILDER"),
        "dsari" to setOf("DSARI")
    )

    private val genericCIIndicators = setOf(
        "CI",
        "CONTINUOUS_INTEGRATION",
        "BUILD_NUMBER",
        "RUN_ID"
    )

    fun detect(): CIDetectionResult {
        val knownSystem = getKnownSystemName()

        return when {
            knownSystem != null -> CIDetectionResult(true, knownSystem)
            isGenericCI -> CIDetectionResult(true, null)
            else -> CIDetectionResult(false, null)
        }
    }

    private fun getKnownSystemName(): String? {
        knownSystems.forEach { (systemName, indicators) ->
            if (indicators.all { hostEnvironmentVariables.containsKey(it) }) {
                return systemName
            }
        }

        if (isCodeship) {
            return "Codeship"
        }

        return null
    }

    private val isCodeship = hostEnvironmentVariables["CI_NAME"] == "codeship"
    private val isGenericCI: Boolean = genericCIIndicators.any { hostEnvironmentVariables.containsKey(it) }
}

data class CIDetectionResult(val suspectRunningOnCI: Boolean, val suspectedCISystem: String?)
