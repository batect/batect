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

package batect.docker.build.buildkit

import batect.docker.build.ImageBuildOutputSink
import batect.docker.build.buildkit.services.AuthService
import batect.docker.build.buildkit.services.FileSyncService
import batect.docker.build.buildkit.services.HealthService
import batect.docker.pull.RegistryCredentialsProvider
import batect.logging.Logger
import batect.logging.LoggerFactory
import batect.os.SystemInfo
import batect.telemetry.TelemetrySessionBuilder
import batect.testutils.createForEachTest
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.describe
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.matches
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okio.Buffer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object BuildKitSessionFactorySpec : Spek({
    describe("a BuildKit session factory") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val buildDirectory by createForEachTest { fileSystem.getPath("/some/project") }
        val homeDirectory by createForEachTest { fileSystem.getPath("/my/home") }
        beforeEachTest { Files.createDirectories(homeDirectory) }

        val outputSink by createForEachTest { mock<ImageBuildOutputSink>() }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { this.homeDirectory } doReturn homeDirectory
            }
        }

        val healthService by createForEachTest { HealthService() }
        val credentialsProvider by createForEachTest { mock<RegistryCredentialsProvider>() }
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val listenerLogger by createForEachTest { mock<Logger>() }
        val authServiceLogger by createForEachTest { mock<Logger>() }
        val fileSyncServiceLogger by createForEachTest { mock<Logger>() }
        val loggerFactory by createForEachTest {
            mock<LoggerFactory> {
                on { createLoggerForClass(GrpcListener::class) } doReturn listenerLogger
                on { createLoggerForClass(AuthService::class) } doReturn authServiceLogger
                on { createLoggerForClass(FileSyncService::class) } doReturn fileSyncServiceLogger
            }
        }

        val factory by createForEachTest { BuildKitSessionFactory(systemInfo, healthService, credentialsProvider, telemetrySessionBuilder, loggerFactory) }

        val dockerConfigDirectory by createForEachTest { fileSystem.getPath("/my/home/.docker") }
        val buildNodeIdFile by createForEachTest { fileSystem.getPath("/my/home/.docker/.buildNodeID") }

        fun Suite.itGeneratesAValidSession(accessor: () -> BuildKitSession) {
            it("generates a session ID from the base-36 character set that is 25 characters long") {
                assertThat(accessor().sessionId, matches("""^[0-9a-z]{25}$""".toRegex()))
            }

            it("generates a build ID that is 32 hex-encoded bytes which is not entirely numeric") {
                assertThat(accessor().buildId, matches("""^[0-9a-f]{64}$""".toRegex()) and !matches("""^[0-9]*$""".toRegex()))
            }

            it("takes the session name from the name of the build directory") {
                assertThat(accessor().name, equalTo("project"))
            }

            it("passes the telemetry session builder to the session") {
                assertThat(accessor().telemetrySessionBuilder, equalTo(telemetrySessionBuilder))
            }

            it("creates a gRPC listener with the session's session ID, the auth service, the health service and a logger") {
                val expectedEndpoints = arrayOf(
                    "/moby.filesync.v1.Auth/Credentials",
                    "/moby.filesync.v1.Auth/FetchToken",
                    "/moby.filesync.v1.Auth/GetTokenAuthority",
                    "/moby.filesync.v1.Auth/VerifyTokenAuthority",
                    "/moby.filesync.v1.FileSync/DiffCopy",
                    "/moby.filesync.v1.FileSync/TarStream",
                    "/grpc.health.v1.Health/Check",
                    "/grpc.health.v1.Health/Watch",
                )

                assertThat(
                    accessor().grpcListener,
                    has(GrpcListener::sessionId, equalTo(accessor().sessionId)) and
                        has(GrpcListener::endpoints, hasKeys(*expectedEndpoints)) and
                        has(GrpcListener::logger, equalTo(listenerLogger))
                )
            }
        }

        fun Suite.itGeneratesAndStoresAValidSharedKey(accessor: () -> BuildKitSession) {
            it("saves the build node ID to disk") {
                assertThat(Files.exists(buildNodeIdFile), equalTo(true))
                assertThat(Files.readAllBytes(buildNodeIdFile).toString(Charsets.UTF_8), matches("""^[0-9a-f]{64}$""".toRegex()))
            }

            it("returns a valid shared key") {
                assertThat(accessor().sharedKey, matches("""^[0-9a-f]{64}$""".toRegex()))
            }

            it("uses the generated node ID when computing the shared key") {
                val nodeId = Files.readAllBytes(buildNodeIdFile).toString(Charsets.UTF_8)
                val buffer = Buffer()
                buffer.writeUtf8(nodeId)
                buffer.writeUtf8(":")
                buffer.writeUtf8(buildDirectory.toString())

                assertThat(accessor().sharedKey, equalTo(buffer.sha256().hex()))
            }
        }

        describe("creating a session") {
            given("the Docker config directory exists") {
                beforeEachTest { Files.createDirectories(dockerConfigDirectory) }

                given("the build node ID file exists") {
                    beforeEachTest {
                        Files.write(
                            buildNodeIdFile,
                            "012345678901234567890123456789012345678901234567890123456789abcd".toByteArray(Charsets.UTF_8)
                        )
                    }

                    val session by createForEachTest { factory.create(buildDirectory, outputSink) }

                    itGeneratesAValidSession { session }

                    it("generates a shared key that is the hex-encoded SHA256 hash of '<build node ID>:<build context directory>'") {
                        assertThat(session.sharedKey, equalTo("094d1c843f788b899d96fae9c6111482f014b04da18641a2bac1e4bfb6e6fa5e"))
                    }
                }

                given("the build node ID file does not exist") {
                    val session by createForEachTest { factory.create(buildDirectory, outputSink) }

                    itGeneratesAValidSession { session }
                    itGeneratesAndStoresAValidSharedKey { session }
                }
            }

            given("the Docker config directory does not exist") {
                val session by createForEachTest { factory.create(buildDirectory, outputSink) }

                itGeneratesAValidSession { session }
                itGeneratesAndStoresAValidSharedKey { session }
            }
        }
    }
})

private fun <KeyType, ValueType> hasKeys(vararg expectedKeys: KeyType): Matcher<Map<KeyType, ValueType>> =
    object : Matcher<Map<KeyType, ValueType>> {
        override fun invoke(actual: Map<KeyType, ValueType>): MatchResult {
            return if (actual.keys == setOf(*expectedKeys)) {
                MatchResult.Match
            } else {
                MatchResult.Mismatch("was: ${describe(actual)}")
            }
        }

        override val description: String
            get() = "has exactly the keys [${expectedKeys.joinToString(",") { describe(it) }}]"

        override val negatedDescription: String
            get() = "does not have exactly the keys [${expectedKeys.joinToString(",") { describe(it) }}]"
    }
