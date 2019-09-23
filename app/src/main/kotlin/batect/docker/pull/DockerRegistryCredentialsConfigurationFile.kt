/*
   Copyright 2017-2019 Charles Korn.

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

package batect.docker.pull

import batect.logging.Logger
import batect.os.ProcessRunner
import batect.utils.Json
import kotlinx.serialization.Serializable
import java.nio.charset.Charset
import java.nio.file.FileSystem
import java.nio.file.Files
import java.util.Properties

class DockerRegistryCredentialsConfigurationFile(
    fileSystem: FileSystem,
    private val processRunner: ProcessRunner,
    private val logger: Logger,
    properties: Properties = System.getProperties()
) {
    private val configFilePath = fileSystem.getPath(properties.getProperty("user.home"), ".docker", "config.json").toAbsolutePath()
    private val config by lazy { loadConfigFile() }

    private fun loadConfigFile(): DockerConfigFile {
        logger.info {
            message("Loading Docker configuration file.")
            data("path", configFilePath.toString())
        }

        if (Files.notExists(configFilePath)) {
            logger.warn {
                message("Docker configuration file does not exist, not attempting to load it. Will use empty configuration instead.")
            }

            return DockerConfigFile()
        }

        val configFileContent = Files.readAllBytes(configFilePath).toString(Charset.defaultCharset())
        return Json.nonstrictParser.parse(DockerConfigFile.serializer(), configFileContent)
    }

    fun getCredentialsForRegistry(registry: String): DockerRegistryCredentialsSource? {
        logger.info {
            message("Getting credentials for registry.")
            data("registry", registry)
        }

        val credHelperForRegistry = config.credHelpers[registry]

        if (credHelperForRegistry != null) {
            logger.info {
                message("Configuration file has a registry-specific credHelper entry.")
                data("registry", registry)
                data("credHelper", credHelperForRegistry)
            }

            return HelperBasedCredentialsSource("docker-credential-$credHelperForRegistry", registry, processRunner)
        }

        if (config.credsStore != null) {
            val credsStore = config.credsStore!!

            logger.info {
                message("Configuration file has global credsStore configured and there is no registry-specific credHelper entry.")
                data("credsStore", credsStore)
            }

            return HelperBasedCredentialsSource("docker-credential-$credsStore", registry, processRunner)
        }

        val authForRegistry = config.auths[registry]

        if (authForRegistry != null) {
            logger.info {
                // Important: don't log the 'auth' value, as that contains either a password or a token.
                message("Configuration file has a registry-specific auths entry and there is no global credsStore nor a registry-specific credHelper entry.")
                data("registry", registry)
            }

            if (authForRegistry.auth == null) {
                logger.warn {
                    message("Configuration file has no 'auth' value within 'auths' entry for registry, will not return any credentials.")
                    data("registry", registry)
                }

                return null
            }

            return BasicCredentialsSource(authForRegistry.auth, registry)
        }

        logger.info {
            message("Configuration file has no global credsStore, and registry does not have a credHelper entry or an auths entry.")
            data("registry", registry)
        }

        return null
    }

    @Serializable
    private data class DockerConfigFile(
        val auths: Map<String, DockerAuth> = emptyMap(),
        val credHelpers: Map<String, String> = emptyMap(),
        val credsStore: String? = null
    )

    @Serializable
    private data class DockerAuth(
        val auth: String? = null
    )
}
