/*
    Copyright 2017-2021 Charles Korn.

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
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// https://github.com/docker/distribution/blob/0ac367fd6bee057d404c405a298b4b7aedf301ec/reference/normalize_test.go is the basis of these tests.
object ImageReferenceSpec : Spek({
    describe("a Docker image reference") {
        given("an empty image name") {
            it("throws an exception") {
                assertThat({ ImageReference("") }, throws<DockerException>(withMessage("Image reference cannot be an empty string.")))
            }
        }

        given("an image name without a tag, registry or repository name") {
            val ref = ImageReference("ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("ubuntu:latest"))
            }
        }

        given("an image name with a tag but without a registry or repository name") {
            val ref = ImageReference("ubuntu:latest")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the provided tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("ubuntu:latest"))
            }
        }

        given("an image name with a digest but without a registry or repository name") {
            val ref = ImageReference("ubuntu@sha256:e6693c20186f837fc393390135d8a598a96a833917917789d63766cab6c59582")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the provided digest when normalized") {
                assertThat(ref.normalizedReference, equalTo("ubuntu@sha256:e6693c20186f837fc393390135d8a598a96a833917917789d63766cab6c59582"))
            }
        }

        given("an image name with a tag and a digest but without a registry or repository name") {
            val ref = ImageReference("ubuntu:latest@sha256:e6693c20186f837fc393390135d8a598a96a833917917789d63766cab6c59582")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the provided digest when normalized") {
                assertThat(ref.normalizedReference, equalTo("ubuntu:latest@sha256:e6693c20186f837fc393390135d8a598a96a833917917789d63766cab6c59582"))
            }
        }

        given("an image name with a repository but no registry name") {
            val ref = ImageReference("library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("library/ubuntu:latest"))
            }
        }

        given("an image name with a repository and the default registry name") {
            val ref = ImageReference("docker.io/library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("docker.io/library/ubuntu:latest"))
            }
        }

        given("an image name with a repository and the legacy default registry name") {
            val ref = ImageReference("index.docker.io/library/ubuntu")

            it("has the default registry domain name") {
                assertThat(ref.registryDomain, equalTo("docker.io"))
            }

            it("has the default registry index") {
                assertThat(ref.registryIndex, equalTo("https://index.docker.io/v1/"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("index.docker.io/library/ubuntu:latest"))
            }
        }

        given("an image name with a local registry and no repository") {
            val ref = ImageReference("localhost/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("localhost"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("localhost"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("localhost/ubuntu:latest"))
            }
        }

        given("an image name with a non-default registry name and no repository") {
            val ref = ImageReference("some-docker-registry.com/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("some-docker-registry.com/ubuntu:latest"))
            }
        }

        given("an image name with a non-default registry name that does not contain a dot but does contain a port and no repository") {
            val ref = ImageReference("some-docker-registry:8080/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry:8080"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry:8080"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("some-docker-registry:8080/ubuntu:latest"))
            }
        }

        given("an image name with a repository and a non-default registry name") {
            val ref = ImageReference("some-docker-registry.com/library/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("some-docker-registry.com/library/ubuntu:latest"))
            }
        }

        given("an image name with a nested repository and a non-default registry name") {
            val ref = ImageReference("some-docker-registry.com/library/linux/ubuntu")

            it("has the provided registry domain name") {
                assertThat(ref.registryDomain, equalTo("some-docker-registry.com"))
            }

            it("uses the registry domain name as the registry index") {
                assertThat(ref.registryIndex, equalTo("some-docker-registry.com"))
            }

            it("has the 'latest' tag when normalized") {
                assertThat(ref.normalizedReference, equalTo("some-docker-registry.com/library/linux/ubuntu:latest"))
            }
        }
    }
})
