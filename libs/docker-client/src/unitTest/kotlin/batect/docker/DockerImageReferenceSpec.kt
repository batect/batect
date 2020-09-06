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

package batect.docker

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerImageReferenceSpec : Spek({
    describe("a Docker image reference") {
        given("an empty image name") {
            val ref = DockerImageReference("")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }
        }

        given("an image name without a registry or repository name") {
            val ref = DockerImageReference("ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }
        }

        given("an image name with a repository but no registry name") {
            val ref = DockerImageReference("library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }
        }

        given("an image name with a repository and the default registry name") {
            val ref = DockerImageReference("docker.io/library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }
        }

        given("an image name with a repository and the legacy default registry name") {
            val ref = DockerImageReference("index.docker.io/library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }
        }

        given("an image name with a local registry and no repository") {
            val ref = DockerImageReference("localhost/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("localhost"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("localhost"))
            }
        }

        given("an image name with a non-default registry name and no repository") {
            val ref = DockerImageReference("some-docker-registry.com/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }
        }

        given("an image name with a non-default registry name that does not contain a dot but does contain a port and no repository") {
            val ref = DockerImageReference("some-docker-registry:8080/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry:8080"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry:8080"))
            }
        }

        given("an image name with a repository and a non-default registry name") {
            val ref = DockerImageReference("some-docker-registry.com/library/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }
        }

        given("an image name with a nested repository and a non-default registry name") {
            val ref = DockerImageReference("some-docker-registry.com/library/linux/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }
        }
    }
})
