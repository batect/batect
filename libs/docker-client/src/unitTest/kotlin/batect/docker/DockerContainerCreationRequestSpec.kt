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

import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object DockerContainerCreationRequestSpec : Spek({
    describe("a Docker container creation request") {
        given("a request with all values provided") {
            val request = DockerContainerCreationRequest(
                "the-container-name",
                DockerImage("the-image"),
                DockerNetwork("the-network"),
                listOf("do-the-thing"),
                listOf("sh"),
                "the-hostname",
                setOf("the-first-network-alias", "the-second-network-alias"),
                mapOf("does.not.exist.com" to "1.2.3.4", "other.com" to "5.6.7.8"),
                mapOf("SOME_VAR" to "some value"),
                "/work-dir",
                setOf(
                    DockerVolumeMount(DockerVolumeMountSource.LocalPath("/local"), "/container-1", "ro"),
                    DockerVolumeMount(DockerVolumeMountSource.Volume("my-volume"), "/container-2", "ro")
                ),
                setOf(DockerDeviceMount("/dev/local", "/dev/container", "rw")),
                setOf(DockerPortMapping(123, 456, "udp"), DockerPortMapping(DockerPortRange(1000, 1001), DockerPortRange(2000, 2001), "my-protocol")),
                DockerHealthCheckConfig(Duration.ofNanos(555), 12, Duration.ofNanos(333), "exit 0"),
                UserAndGroup(789, 222),
                privileged = true,
                init = true,
                capabilitiesToAdd = setOf(Capability.NET_ADMIN, Capability.KILL),
                capabilitiesToDrop = setOf(Capability.AUDIT_READ, Capability.CHOWN),
                useTTY = true,
                attachStdin = false,
                logDriver = "some-log-driver",
                logOptions = mapOf("option-1" to "value-1")
            )

            on("converting it to JSON for submission to the Docker API") {
                val json = request.toJson()

                it("returns the request in the format expected by the Docker API") {
                    assertThat(json, equivalentTo("""{
                        |   "AttachStdin": false,
                        |   "AttachStdout": true,
                        |   "AttachStderr": true,
                        |   "Tty": true,
                        |   "OpenStdin": false,
                        |   "StdinOnce": false,
                        |   "Image": "the-image",
                        |   "Cmd": ["do-the-thing"],
                        |   "Entrypoint": ["sh"],
                        |   "Hostname": "the-hostname",
                        |   "WorkingDir": "/work-dir",
                        |   "User": "789:222",
                        |   "Env": [
                        |       "SOME_VAR=some value"
                        |   ],
                        |   "ExposedPorts": {
                        |       "456/udp": {},
                        |       "2000/my-protocol": {},
                        |       "2001/my-protocol": {}
                        |   },
                        |   "HostConfig": {
                        |       "NetworkMode": "the-network",
                        |       "Binds": [
                        |           "/local:/container-1:ro",
                        |           "my-volume:/container-2:ro"
                        |       ],
                        |       "Devices": [
                        |           {
                        |               "PathOnHost": "/dev/local",
                        |               "PathInContainer": "/dev/container",
                        |               "CgroupPermissions": "rw"
                        |           }
                        |       ],
                        |       "PortBindings": {
                        |           "456/udp": [
                        |               {
                        |                   "HostIp": "",
                        |                   "HostPort": "123"
                        |               }
                        |           ],
                        |           "2000/my-protocol": [
                        |               {
                        |                   "HostIp": "",
                        |                   "HostPort": "1000"
                        |               }
                        |           ],
                        |           "2001/my-protocol": [
                        |               {
                        |                   "HostIp": "",
                        |                   "HostPort": "1001"
                        |               }
                        |           ]
                        |       },
                        |       "Privileged": true,
                        |       "Init": true,
                        |       "CapAdd": ["NET_ADMIN", "KILL"],
                        |       "CapDrop": ["AUDIT_READ", "CHOWN"],
                        |       "LogConfig": { "Type": "some-log-driver", "Config": { "option-1": "value-1" } },
                        |       "ExtraHosts": [ "does.not.exist.com:1.2.3.4", "other.com:5.6.7.8" ]
                        |   },
                        |   "Healthcheck": {
                        |       "Test": ["CMD-SHELL", "exit 0"],
                        |       "Interval": 555,
                        |       "Retries": 12,
                        |       "StartPeriod": 333
                        |   },
                        |   "NetworkingConfig": {
                        |       "EndpointsConfig": {
                        |           "the-network": {
                        |               "Aliases": [
                        |                   "the-first-network-alias",
                        |                   "the-second-network-alias"
                        |               ]
                        |           }
                        |       }
                        |   }
                        |}""".trimMargin()))
                }
            }

            on("converting it to JSON for logging") {
                val json = Json.default.stringify(DockerContainerCreationRequest.serializer(), request)

                it("returns a JSON representation of the Kotlin object") {
                    assertThat(json, equivalentTo("""
                        |{
                        |  "name": "the-container-name",
                        |  "image": { "id": "the-image" },
                        |  "network": { "id": "the-network" },
                        |  "command": ["do-the-thing"],
                        |  "entrypoint": ["sh"],
                        |  "hostname": "the-hostname",
                        |  "networkAliases": [
                        |    "the-first-network-alias",
                        |    "the-second-network-alias"
                        |  ],
                        |  "extraHosts": { "does.not.exist.com": "1.2.3.4", "other.com": "5.6.7.8" },
                        |  "environmentVariables": {
                        |    "SOME_VAR": "some value"
                        |  },
                        |  "workingDirectory": "/work-dir",
                        |  "volumeMounts": [
                        |    {
                        |      "source": {
                        |        "type": "batect.docker.DockerVolumeMountSource.LocalPath",
                        |        "formatted": "/local",
                        |        "path": "/local"
                        |      },
                        |      "containerPath": "/container-1",
                        |      "options": "ro"
                        |    },
                        |    {
                        |      "source": {
                        |        "type": "batect.docker.DockerVolumeMountSource.Volume",
                        |        "formatted": "my-volume",
                        |        "name": "my-volume"
                        |      },
                        |      "containerPath": "/container-2",
                        |      "options": "ro"
                        |    }
                        |  ],
                        |  "deviceMounts": [
                        |    {
                        |      "localPath": "/dev/local",
                        |      "containerPath": "/dev/container",
                        |      "options": "rw"
                        |    }
                        |  ],
                        |  "portMappings": [
                        |    { "local": { "from": 123, "to": 123 }, "container": { "from": 456, "to": 456 }, "protocol": "udp" },
                        |    { "local": { "from": 1000, "to": 1001 }, "container": { "from": 2000, "to": 2001 }, "protocol": "my-protocol" }
                        |  ],
                        |  "healthCheckConfig": {
                        |    "interval": "555ns",
                        |    "retries": 12,
                        |    "startPeriod": "333ns",
                        |    "command": "exit 0"
                        |  },
                        |  "userAndGroup": { "userId": 789, "groupId": 222 },
                        |  "privileged": true,
                        |  "init": true,
                        |  "capabilitiesToAdd": ["NET_ADMIN", "KILL"],
                        |  "capabilitiesToDrop": ["AUDIT_READ", "CHOWN"],
                        |  "useTTY": true,
                        |  "attachStdin": false,
                        |  "logDriver": "some-log-driver",
                        |  "logOptions": { "option-1": "value-1" }
                        |}
                    """.trimMargin()))
                }
            }
        }

        given("a request with only the minimal set of values provided") {
            val request = DockerContainerCreationRequest(
                "the-container-name",
                DockerImage("the-image"),
                DockerNetwork("the-network"),
                emptyList(),
                emptyList(),
                "the-hostname",
                setOf("the-network-alias"),
                emptyMap(),
                emptyMap(),
                null,
                emptySet(),
                emptySet(),
                emptySet(),
                DockerHealthCheckConfig(),
                null,
                privileged = false,
                init = false,
                capabilitiesToAdd = emptySet(),
                capabilitiesToDrop = emptySet(),
                useTTY = false,
                attachStdin = false,
                logDriver = "json-file",
                logOptions = emptyMap()
            )

            on("converting it to JSON for submission to the Docker API") {
                val json = request.toJson()

                it("returns the request in the format expected by the Docker API") {
                    assertThat(json, equivalentTo("""{
                        |   "AttachStdin": false,
                        |   "AttachStdout": true,
                        |   "AttachStderr": true,
                        |   "Tty": false,
                        |   "OpenStdin": false,
                        |   "StdinOnce": false,
                        |   "Image": "the-image",
                        |   "Hostname": "the-hostname",
                        |   "Env": [],
                        |   "ExposedPorts": {},
                        |   "HostConfig": {
                        |       "NetworkMode": "the-network",
                        |       "Binds": [],
                        |       "Devices": [],
                        |       "PortBindings": {},
                        |       "Privileged": false,
                        |       "Init": false,
                        |       "CapAdd": [],
                        |       "CapDrop": [],
                        |       "LogConfig": { "Type": "json-file", "Config": {} },
                        |       "ExtraHosts": []
                        |   },
                        |   "Healthcheck": {
                        |       "Test": [],
                        |       "Interval": 0,
                        |       "Retries": 0,
                        |       "StartPeriod": 0
                        |   },
                        |   "NetworkingConfig": {
                        |       "EndpointsConfig": {
                        |           "the-network": {
                        |               "Aliases": [
                        |                   "the-network-alias"
                        |               ]
                        |           }
                        |       }
                        |   }
                        |}""".trimMargin()))
                }
            }
        }
    }
})
