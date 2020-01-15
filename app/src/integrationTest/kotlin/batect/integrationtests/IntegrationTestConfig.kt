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
import batect.os.NativeMethods
import batect.os.SystemInfo
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsNativeMethods
import jnr.ffi.Platform
import jnr.posix.POSIX
import okhttp3.OkHttpClient
import java.nio.file.Path
import java.nio.file.Paths

val testImagesDirectory: Path = Paths.get("src", "integrationTest", "resources", "test-images").toAbsolutePath()

fun getDockerHost(systemInfo: SystemInfo): String =
    System.getenv().getOrDefault("DOCKER_HOST", DockerHttpConfigDefaults(systemInfo).defaultDockerHost)

fun getDockerTLSConfig(): DockerTLSConfig {
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

fun getDockerHttpConfig(systemInfo: SystemInfo): DockerHttpConfig {
    val dockerHost = getDockerHost(systemInfo)
    val tlsConfig = getDockerTLSConfig()

    return DockerHttpConfig(OkHttpClient(), dockerHost, tlsConfig, systemInfo)
}

fun getNativeMethodsForPlatform(posix: POSIX): NativeMethods = when (Platform.getNativePlatform().os) {
    Platform.OS.WINDOWS -> WindowsNativeMethods(posix)
    else -> UnixNativeMethods(posix)
}
