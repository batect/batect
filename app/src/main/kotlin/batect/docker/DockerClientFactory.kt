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
import batect.dockerclient.DockerClient
import batect.dockerclient.TLSVerification
import okio.Path.Companion.toOkioPath
import java.nio.file.Files

// FIXME: this has no tests, because there's no easy way to examine the configured
// options for DockerClient.Builder().
class DockerClientFactory(
    private val commandLineOptions: CommandLineOptions
) {
    fun createBuilder(): DockerClient.Builder {
        val builder = DockerClient.Builder()
            .withHost(commandLineOptions.dockerHost)
            .doNotUseDefaultConfigurationFromEnvironment()

        // If the user has specified a config directory, that is validated by CommandLineOptionsParser.
        // However, if we're using the default value, then no validation is performed, so there's a chance
        // the directory does not exist in this case.
        if (Files.exists(commandLineOptions.dockerConfigDirectory)) {
            builder.withConfigDirectory(commandLineOptions.dockerConfigDirectory.toOkioPath())
        }

        if (commandLineOptions.dockerUseTLS) {
            builder.withTLSConfiguration(
                commandLineOptions.dockerTlsCACertificatePath.toOkioPath(),
                commandLineOptions.dockerTLSCertificatePath.toOkioPath(),
                commandLineOptions.dockerTLSKeyPath.toOkioPath(),
                if (commandLineOptions.dockerVerifyTLS) TLSVerification.Enabled else TLSVerification.InsecureDisabled
            )
        }

        return builder
    }
}
