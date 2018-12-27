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

package batect.docker.pull

import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files
import java.util.Properties

object DockerRegistryCredentialsConfigurationFileSpec : Spek({
    describe("a Docker registry credentials configuration file") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val logger by createLoggerForEachTest()

        val properties = Properties()
        properties.setProperty("user.home", "/home/some-user")

        val configFile by createForEachTest { DockerRegistryCredentialsConfigurationFile(fileSystem, processRunner, logger, properties) }

        val configurationFilePath by createForEachTest { fileSystem.getPath("/home/some-user/.docker/config.json") }

        beforeEachTest {
            Files.createDirectories(configurationFilePath.parent)
        }

        given("the configuration file does not exist") {
            on("getting credentials for a registry") {
                val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                it("returns no credentials") {
                    assertThat(credentials, absent())
                }
            }
        }

        given("the configuration file exists but is empty") {
            beforeEachTest {
                Files.write(configurationFilePath, listOf("{}"))
            }

            on("getting credentials for a registry") {
                val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                it("returns no credentials") {
                    assertThat(credentials, absent())
                }
            }
        }

        given("the configuration file exists and has an empty list of credentials") {
            given("and it has no credential helpers configured") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {},
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-awesomesauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {}
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns no credentials") {
                            assertThat(credentials, absent())
                        }
                    }
                }
            }

            given("and it has a credential helper configured for the registry") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {},
                            |   "credHelpers": {
                            |       "someregistry.com": "specialsauce"
                            |   },
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store for the registry") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-specialsauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {},
                            |   "credHelpers": {
                            |       "someregistry.com": "specialsauce"
                            |   }
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store for the registry") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-specialsauce", "someregistry.com", processRunner)))
                        }
                    }
                }
            }

            given("and it has a credential helper configured for another registry") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {},
                            |   "credHelpers": {
                            |       "someotherregistry.com": "specialsauce"
                            |   },
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-awesomesauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {},
                            |   "credHelpers": {
                            |       "someotherregistry.com": "specialsauce"
                            |   }
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for the registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns no credentials") {
                            assertThat(credentials, absent())
                        }
                    }
                }
            }
        }

        given("the configuration file exists and has a list of credentials") {
            given("and it has no credential helpers configured") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       }
                            |   },
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-awesomesauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       },
                            |       "someregistrywithnoauth.com": {
                            |       }
                            |   },
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry with credentials specified") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns those credentials") {
                            assertThat(credentials, equalTo(BasicCredentialsSource("somecreds", "someregistry.com")))
                        }
                    }

                    on("getting credentials for a registry with credentials specified but no 'auth' value") {
                        val credentials = configFile.getCredentialsForRegistry("someregistrywithnoauth.com")

                        it("returns no credentials") {
                            assertThat(credentials, absent())
                        }
                    }

                    on("getting credentials for a registry with no credentials specified") {
                        val credentials = configFile.getCredentialsForRegistry("someotherregistry.com")

                        it("returns no credentials") {
                            assertThat(credentials, absent())
                        }
                    }
                }
            }

            given("and it has a credential helper configured for the registry") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       }
                            |   },
                            |   "credHelpers": {
                            |       "someregistry.com": "specialsauce"
                            |   },
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store for the registry") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-specialsauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       }
                            |   },
                            |   "credHelpers": {
                            |       "someregistry.com": "specialsauce"
                            |   }
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store for the registry") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-specialsauce", "someregistry.com", processRunner)))
                        }
                    }
                }
            }

            given("and it has a credential helper configured for another registry") {
                given("and it has a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       }
                            |   },
                            |   "credHelpers": {
                            |       "someotherregistry.com": "specialsauce"
                            |   },
                            |   "credsStore": "awesomesauce"
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for a registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns credentials from the credential store") {
                            assertThat(credentials, equalTo(HelperBasedCredentialsSource("docker-credential-awesomesauce", "someregistry.com", processRunner)))
                        }
                    }
                }

                given("and it does not have a credential store configured") {
                    beforeEachTest {
                        Files.write(configurationFilePath, """
                            |{
                            |   "auths": {
                            |       "someregistry.com": {
                            |           "auth": "somecreds"
                            |       }
                            |   },
                            |   "credHelpers": {
                            |       "someotherregistry.com": "specialsauce"
                            |   }
                            |}
                        """.trimMargin().lines())
                    }

                    on("getting credentials for the registry") {
                        val credentials = configFile.getCredentialsForRegistry("someregistry.com")

                        it("returns the provided credentials") {
                            assertThat(credentials, equalTo(BasicCredentialsSource("somecreds", "someregistry.com")))
                        }
                    }
                }
            }
        }
    }
})
