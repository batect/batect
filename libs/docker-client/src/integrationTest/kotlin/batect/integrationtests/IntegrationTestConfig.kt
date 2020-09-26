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

package batect.integrationtests

import batect.docker.DockerHttpConfig
import batect.docker.DockerHttpConfigDefaults
import batect.docker.DockerTLSConfig
import batect.docker.api.ContainersAPI
import batect.docker.api.ExecAPI
import batect.docker.api.ImagesAPI
import batect.docker.api.NetworksAPI
import batect.docker.api.SystemInfoAPI
import batect.docker.api.VolumesAPI
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.client.DockerClient
import batect.docker.client.DockerContainersClient
import batect.docker.client.DockerExecClient
import batect.docker.client.DockerImagesClient
import batect.docker.client.DockerNetworksClient
import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVolumesClient
import batect.docker.pull.DockerRegistryCredentialsConfigurationFile
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.logging.Logger
import batect.os.ConsoleDimensions
import batect.os.ConsoleInfo
import batect.os.ConsoleManager
import batect.os.HostEnvironmentVariables
import batect.os.NativeMethods
import batect.os.ProcessRunner
import batect.os.SignalListener
import batect.os.SystemInfo
import batect.os.unix.ApplicationResolver
import batect.os.unix.UnixConsoleManager
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsConsoleManager
import batect.os.windows.WindowsNativeMethods
import com.nhaarman.mockitokotlin2.mock
import jnr.ffi.Platform
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import org.jsoftbiz.utils.OS
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

val testImagesDirectory: Path = Paths.get("src", "integrationTest", "resources", "test-images").toAbsolutePath()

fun createClient(posix: POSIX = POSIXFactory.getNativePOSIX(), nativeMethods: NativeMethods = getNativeMethodsForPlatform(posix)): DockerClient {
    val logger = mock<Logger>()
    val processRunner = ProcessRunner(logger)
    val systemInfo = SystemInfo(nativeMethods, FileSystems.getDefault(), OS.getOs())
    val dockerHost = getDockerHost(systemInfo)
    val tlsConfig = getDockerTLSConfig()
    val httpConfig = DockerHttpConfig(OkHttpClient(), dockerHost, tlsConfig, systemInfo)
    val containersAPI = ContainersAPI(httpConfig, systemInfo, logger)
    val execAPI = ExecAPI(httpConfig, systemInfo, logger)
    val imagesAPI = ImagesAPI(httpConfig, systemInfo, logger)
    val networksAPI = NetworksAPI(httpConfig, systemInfo, logger)
    val systemInfoAPI = SystemInfoAPI(httpConfig, systemInfo, logger)
    val volumesAPI = VolumesAPI(httpConfig, systemInfo, logger)
    val consoleInfo = ConsoleInfo(nativeMethods, systemInfo, HostEnvironmentVariables.current, logger)
    val consoleManager = getConsoleManagerForPlatform(consoleInfo, processRunner, nativeMethods, logger)
    val credentialsConfigurationFile = DockerRegistryCredentialsConfigurationFile(FileSystems.getDefault(), processRunner, logger)
    val credentialsProvider = DockerRegistryCredentialsProvider(credentialsConfigurationFile)
    val ignoreParser = DockerIgnoreParser()
    val imageBuildContextFactory = DockerImageBuildContextFactory(ignoreParser)
    val dockerfileParser = DockerfileParser()
    val waiter = ContainerWaiter(containersAPI)
    val streamer = ContainerIOStreamer()
    val signalListener = SignalListener(posix)
    val consoleDimensions = ConsoleDimensions(nativeMethods, signalListener, logger)
    val ttyManager = ContainerTTYManager(containersAPI, consoleDimensions, logger)

    val containersClient = DockerContainersClient(containersAPI, consoleManager, waiter, streamer, ttyManager, logger)
    val execClient = DockerExecClient(execAPI, streamer, logger)
    val imagesClient = DockerImagesClient(imagesAPI, credentialsProvider, imageBuildContextFactory, dockerfileParser, logger)
    val networksClient = DockerNetworksClient(networksAPI)
    val systemInfoClient = DockerSystemInfoClient(systemInfoAPI, mock(), logger)
    val volumesClient = DockerVolumesClient(volumesAPI)

    return DockerClient(containersClient, execClient, imagesClient, networksClient, systemInfoClient, volumesClient)
}

private fun getDockerHost(systemInfo: SystemInfo): String =
    System.getenv().getOrDefault("DOCKER_HOST", DockerHttpConfigDefaults(systemInfo).defaultDockerHost)

private fun getDockerTLSConfig(): DockerTLSConfig {
    if (System.getenv().getOrDefault("DOCKER_TLS_VERIFY", "0") != "1") {
        return DockerTLSConfig.DisableTLS
    }

    val certsDir = Paths.get(System.getenv().getValue("DOCKER_CERT_PATH"))

    return DockerTLSConfig.EnableTLS(
        true,
        certsDir.resolve("ca.pem"),
        certsDir.resolve("cert.pem"),
        certsDir.resolve("key.pem")
    )
}

private fun getConsoleManagerForPlatform(consoleInfo: ConsoleInfo, processRunner: ProcessRunner, nativeMethods: NativeMethods, logger: Logger): ConsoleManager =
    when (Platform.getNativePlatform().os) {
        Platform.OS.WINDOWS -> WindowsConsoleManager(consoleInfo, nativeMethods as WindowsNativeMethods, logger)
        else -> UnixConsoleManager(consoleInfo, ApplicationResolver(FileSystems.getDefault()), processRunner, logger)
    }

fun getNativeMethodsForPlatform(posix: POSIX): NativeMethods = when (Platform.getNativePlatform().os) {
    Platform.OS.WINDOWS -> WindowsNativeMethods(posix)
    else -> UnixNativeMethods(posix)
}
