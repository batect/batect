/*
   Copyright 2017-2018 Charles Korn.

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

package batect.docker

class DockerContainerCreationCommandGenerator {
    fun createCommandLine(request: DockerContainerCreationRequest): Iterable<String> {
        return listOf("docker", "create", "-it",
            "--network", request.network.id,
            "--hostname", request.hostname,
            "--network-alias", request.networkAlias) +
            environmentVariableArguments(request) +
            workingDirectoryArguments(request) +
            volumeMountArguments(request) +
            portMappingArguments(request) +
            healthCheckArguments(request) +
            userArguments(request) +
            request.image.id +
            request.command
    }

    private fun environmentVariableArguments(request: DockerContainerCreationRequest): Iterable<String> {
        return request.environmentVariables.flatMap { (key, value) -> listOf("--env", "$key=$value") }
    }

    private fun volumeMountArguments(request: DockerContainerCreationRequest): Iterable<String> = request.volumeMounts.flatMap { listOf("--volume", it.toString()) }
    private fun portMappingArguments(request: DockerContainerCreationRequest): Iterable<String> = request.portMappings.flatMap { listOf("--publish", it.toString()) }

    private fun workingDirectoryArguments(request: DockerContainerCreationRequest): Iterable<String> = when (request.workingDirectory) {
        null -> emptyList()
        else -> listOf("--workdir", request.workingDirectory)
    }

    private fun healthCheckArguments(request: DockerContainerCreationRequest): Iterable<String> {
        return healthCheckIntervalArguments(request) +
            healthCheckRetriesArguments(request) +
            healthCheckStartPeriodArguments(request)
    }

    private fun healthCheckIntervalArguments(request: DockerContainerCreationRequest): Iterable<String> = when (request.healthCheckConfig.interval) {
        null -> emptyList()
        else -> listOf("--health-interval", request.healthCheckConfig.interval)
    }

    private fun healthCheckRetriesArguments(request: DockerContainerCreationRequest): Iterable<String> = when (request.healthCheckConfig.retries) {
        null -> emptyList()
        else -> listOf("--health-retries", request.healthCheckConfig.retries.toString())
    }

    private fun healthCheckStartPeriodArguments(request: DockerContainerCreationRequest): Iterable<String> = when (request.healthCheckConfig.startPeriod) {
        null -> emptyList()
        else -> listOf("--health-start-period", request.healthCheckConfig.startPeriod)
    }

    private fun userArguments(request: DockerContainerCreationRequest): Iterable<String> = when (request.userAndGroup) {
        null -> emptyList()
        else -> listOf("--user", "${request.userAndGroup.userId}:${request.userAndGroup.groupId}")
    }
}
