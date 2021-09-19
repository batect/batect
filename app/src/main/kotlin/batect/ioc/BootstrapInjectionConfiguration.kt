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

package batect.ioc

import batect.VersionInfo
import batect.cli.CommandLineOptionsParser
import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.docker.DockerHttpConfigDefaults
import batect.os.HostEnvironmentVariables
import batect.os.NativeMethods
import batect.os.PathResolverFactory
import batect.os.SystemInfo
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsNativeMethods
import batect.telemetry.TelemetrySessionBuilder
import com.hypirion.io.RevivableInputStream
import jnr.ffi.Platform
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import org.jsoftbiz.utils.OS
import org.kodein.di.DI
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems

fun createKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream, inputStream: InputStream): DirectDI = DI.direct {
    bind<FileSystem>() with singleton { FileSystems.getDefault() }
    bind<POSIX>() with singleton { POSIXFactory.getNativePOSIX() }
    bind<PrintStream>(StreamType.Error) with instance(errorStream)
    bind<PrintStream>(StreamType.Output) with instance(outputStream)
    bind<RevivableInputStream>(StreamType.Input) with instance(RevivableInputStream(inputStream))

    import(cliModule)
    import(dockerModule)
    import(telemetryModule)
    import(osModule)

    if (Platform.getNativePlatform().os in setOf(Platform.OS.DARWIN, Platform.OS.LINUX)) {
        bind<NativeMethods>() with singleton { UnixNativeMethods(instance()) }
    }

    if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
        bind<WindowsNativeMethods>() with singleton { WindowsNativeMethods(instance()) }
        bind<NativeMethods>() with singleton { instance<WindowsNativeMethods>() }
    }
}

private val cliModule = DI.Module("bootstrap cli") {
    bind<CommandLineOptionsParser>() with singleton { CommandLineOptionsParser(instance(), instance(), instance(), instance()) }
    bind<EnvironmentVariableDefaultValueProviderFactory>() with singleton { EnvironmentVariableDefaultValueProviderFactory(instance()) }
}

private val dockerModule = DI.Module("bootstrap docker") {
    bind<DockerHttpConfigDefaults>() with singleton { DockerHttpConfigDefaults(instance()) }
}

private val telemetryModule = DI.Module("bootstrap telemetry") {
    bind<TelemetrySessionBuilder>() with instance(TelemetrySessionBuilder(VersionInfo()))
}

private val osModule = DI.Module("bootstrap os") {
    bind<HostEnvironmentVariables>() with singleton { HostEnvironmentVariables.current }
    bind<PathResolverFactory>() with singleton { PathResolverFactory(instance()) }
    bind<SystemInfo>() with singleton { SystemInfo(instance(), instance(), instance()) }
    bind<OS>() with singleton { OS.getOs() }
}
