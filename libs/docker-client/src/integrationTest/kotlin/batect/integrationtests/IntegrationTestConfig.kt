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

package batect.integrationtests

import batect.docker.DockerHttpConfig
import batect.docker.DockerHttpConfigDefaults
import batect.docker.DockerTLSConfig
import batect.docker.api.ContainersAPI
import batect.docker.api.ExecAPI
import batect.docker.api.ImagesAPI
import batect.docker.api.NetworksAPI
import batect.docker.api.SessionsAPI
import batect.docker.api.SystemInfoAPI
import batect.docker.api.VolumesAPI
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerfileParser
import batect.docker.build.ImageBuildContextFactory
import batect.docker.build.buildkit.BuildKitSessionFactory
import batect.docker.build.buildkit.services.HealthService
import batect.docker.client.ContainersClient
import batect.docker.client.DockerClient
import batect.docker.client.ExecClient
import batect.docker.client.ImagesClient
import batect.docker.client.NetworksClient
import batect.docker.client.SystemInfoClient
import batect.docker.client.VolumesClient
import batect.docker.pull.RegistryCredentialsConfigurationFile
import batect.docker.pull.RegistryCredentialsProvider
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.logging.Logger
import batect.logging.LoggerFactory
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
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
    val loggerFactory = mock<LoggerFactory> {
        on { createLoggerForClass(any()) } doReturn logger
    }

    val processRunner = ProcessRunner(logger)
    val systemInfo = SystemInfo(nativeMethods, FileSystems.getDefault(), OS.getOs())
    val dockerHost = getDockerHost(systemInfo)
    val tlsConfig = getDockerTLSConfig()
    val httpConfig = DockerHttpConfig(OkHttpClient(), dockerHost, tlsConfig, systemInfo)
    val containersAPI = ContainersAPI(httpConfig, systemInfo, logger)
    val execAPI = ExecAPI(httpConfig, systemInfo, logger)
    val imagesAPI = ImagesAPI(httpConfig, systemInfo, logger)
    val networksAPI = NetworksAPI(httpConfig, systemInfo, logger)
    val sessionsAPI = SessionsAPI(httpConfig, systemInfo, logger)
    val systemInfoAPI = SystemInfoAPI(httpConfig, systemInfo, logger)
    val volumesAPI = VolumesAPI(httpConfig, systemInfo, logger)
    val consoleInfo = ConsoleInfo(nativeMethods, systemInfo, HostEnvironmentVariables.current, logger)
    val consoleManager = getConsoleManagerForPlatform(consoleInfo, processRunner, nativeMethods, logger)
    val credentialsConfigurationFile = RegistryCredentialsConfigurationFile(processRunner, systemInfo.homeDirectory.resolve(".docker"), logger)
    val credentialsProvider = RegistryCredentialsProvider(credentialsConfigurationFile)
    val ignoreParser = DockerIgnoreParser()
    val imageBuildContextFactory = ImageBuildContextFactory(ignoreParser)
    val dockerfileParser = DockerfileParser()
    val waiter = ContainerWaiter(containersAPI)
    val streamer = ContainerIOStreamer()
    val signalListener = SignalListener(posix)
    val consoleDimensions = ConsoleDimensions(nativeMethods, signalListener, logger)
    val ttyManager = ContainerTTYManager(containersAPI, consoleDimensions, logger)
    val buildKitSessionFactory = BuildKitSessionFactory(systemInfo, HealthService(), credentialsProvider, mock(), loggerFactory)

    val containersClient = ContainersClient(containersAPI, consoleManager, waiter, streamer, ttyManager, logger)
    val execClient = ExecClient(execAPI, streamer, logger)
    val imagesClient = ImagesClient(imagesAPI, sessionsAPI, credentialsProvider, imageBuildContextFactory, dockerfileParser, buildKitSessionFactory, logger)
    val networksClient = NetworksClient(networksAPI)
    val systemInfoClient = SystemInfoClient(systemInfoAPI, mock(), logger)
    val volumesClient = VolumesClient(volumesAPI)

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
