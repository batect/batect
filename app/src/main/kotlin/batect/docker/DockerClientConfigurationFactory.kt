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

package batect.docker

import batect.cli.CommandLineOptions
import batect.dockerclient.DockerCLIContext
import batect.dockerclient.DockerClientConfiguration
import okio.Path.Companion.toOkioPath
import java.nio.file.Files

class DockerClientConfigurationFactory(
    private val commandLineOptions: CommandLineOptions,
) {
    fun createConfiguration(): DockerClientConfiguration {
        return when (commandLineOptions.docker.contextName) {
            DockerCLIContext.default.name -> createDefaultConfiguration()
            else -> loadContext()
        }
    }

    private fun createDefaultConfiguration(): DockerClientConfiguration {
        val options = commandLineOptions.docker
        val builder = DockerClientConfiguration.Builder(options.host)

        // If the user has specified a config directory, that is validated by CommandLineOptionsParser.
        // However, if we're using the default value, then no validation is performed, so there's a chance
        // the directory does not exist in this case.
        if (Files.exists(options.configDirectory)) {
            builder.withConfigurationDirectory(options.configDirectory.toOkioPath())
        }

        if (options.useTLS) {
            builder.withTLSConfiguration(
                options.tlsCACertificatePath.toOkioPath(),
                options.tlsCertificatePath.toOkioPath(),
                options.tlsKeyPath.toOkioPath(),
            )

            if (!options.verifyTLS) {
                builder.withDaemonIdentityVerificationDisabled()
            }
        }

        return builder.build()
    }

    private fun loadContext(): DockerClientConfiguration {
        val options = commandLineOptions.docker

        return DockerClientConfiguration.fromCLIContext(DockerCLIContext(options.contextName), options.configDirectory.toOkioPath())
    }
}
