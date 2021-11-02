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

package batect

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.CommandLineOptionsParsingResult
import batect.cli.commands.CommandFactory
import batect.ioc.StreamType
import batect.ioc.createKodeinConfiguration
import batect.logging.ApplicationInfoLogger
import batect.logging.logger
import batect.os.ConsoleManager
import batect.os.SystemInfo
import batect.telemetry.EnvironmentTelemetryCollector
import batect.telemetry.TelemetryConsentPrompt
import batect.telemetry.TelemetryManager
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import batect.ui.Console
import batect.ui.text.Text
import batect.wrapper.WrapperCache
import org.kodein.di.DirectDI
import org.kodein.di.DirectDIAware
import org.kodein.di.instance
import java.io.InputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application(System.out, System.err, System.`in`).run(args.toList())
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
        exitProcess(-1)
    }
}

class Application(override val directDI: DirectDI) : DirectDIAware {
    constructor(outputStream: PrintStream, errorStream: PrintStream, inputStream: InputStream) :
        this(createKodeinConfiguration(outputStream, errorStream, inputStream))

    private val telemetrySessionBuilder: TelemetrySessionBuilder = instance()
    private val errorStream: PrintStream = instance(StreamType.Error)
    private val commandLineOptionsParser: CommandLineOptionsParser = instance()
    private val systemInfo: SystemInfo = instance()

    fun run(args: Iterable<String>): Int {
        if (!systemInfo.isSupportedOperatingSystem) {
            errorStream.println("Batect only supports Linux, macOS and Windows.")
            return -1
        }

        return when (val result = commandLineOptionsParser.parse(args)) {
            is CommandLineOptionsParsingResult.Succeeded -> run(result.options, args)
            is CommandLineOptionsParsingResult.Failed -> handleOptionsParsingFailed(result)
        }
    }

    private fun run(options: CommandLineOptions, args: Iterable<String>): Int {
        val extendedKodein = options.extend(directDI)

        // Why not do this in run() above? Because we have to wait until we know we've successfully parsed the command line arguments
        // to ensure that we can respect any telemetry-related requests.
        val telemetryManager = extendedKodein.instance<TelemetryManager>()
        val exitCode = runCommand(options, args, extendedKodein)

        telemetrySessionBuilder.addAttribute("exitCode", exitCode)
        telemetryManager.finishSession(telemetrySessionBuilder)

        return exitCode
    }

    private fun runCommand(options: CommandLineOptions, args: Iterable<String>, extendedKodein: DirectDI): Int {
        val logger = extendedKodein.logger<Application>()
        val consoleManager = extendedKodein.instance<ConsoleManager>()
        val errorConsole = extendedKodein.instance<Console>(StreamType.Error)
        val wrapperCache = extendedKodein.instance<WrapperCache>()
        val commandFactory = extendedKodein.instance<CommandFactory>()
        val environmentTelemetryCollector = extendedKodein.instance<EnvironmentTelemetryCollector>()

        try {
            val applicationInfoLogger = extendedKodein.instance<ApplicationInfoLogger>()
            applicationInfoLogger.logApplicationInfo(args)

            consoleManager.enableConsoleEscapeSequences()
            wrapperCache.setLastUsedForCurrentVersion()

            val command = commandFactory.createCommand(options, extendedKodein)
            environmentTelemetryCollector.collect(command::class)

            return command.run()
        } catch (e: Throwable) {
            errorConsole.println(Text.red(e.toString()))

            logger.error {
                message("Exception thrown during execution.")
                exception(e)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)

            return -1
        }
    }

    private fun handleOptionsParsingFailed(result: CommandLineOptionsParsingResult.Failed): Int {
        // We can't use the Console object here because those aren't available until after options parsing has been completed
        // (because we need to respect things like --no-color).
        errorStream.println(result.message)
        return -1
    }
}
