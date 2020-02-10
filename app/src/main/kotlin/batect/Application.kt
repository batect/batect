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
import batect.ui.Console
import batect.ui.text.Text
import org.kodein.di.DKodein
import org.kodein.di.DKodeinAware
import org.kodein.di.generic.instance
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

class Application(override val dkodein: DKodein) : DKodeinAware {
    constructor(outputStream: PrintStream, errorStream: PrintStream, inputStream: InputStream) :
        this(createKodeinConfiguration(outputStream, errorStream, inputStream))

    private val errorStream: PrintStream = instance(StreamType.Error)
    private val commandLineOptionsParser: CommandLineOptionsParser = instance()
    private val commandFactory: CommandFactory = instance()
    private val systemInfo: SystemInfo = instance()

    fun run(args: Iterable<String>): Int {
        if (!systemInfo.isSupportedOperatingSystem) {
            errorStream.println("batect only supports Linux, OS X and Windows.")
            return -1
        }

        return when (val result = commandLineOptionsParser.parse(args)) {
            is CommandLineOptionsParsingResult.Succeeded -> runCommand(result.options, args)
            is CommandLineOptionsParsingResult.Failed -> handleOptionsParsingFailed(result)
        }
    }

    private fun runCommand(options: CommandLineOptions, args: Iterable<String>): Int {
        val extendedKodein = options.extend(dkodein)
        val logger = extendedKodein.logger<Application>()
        val consoleManager = extendedKodein.instance<ConsoleManager>()
        val errorConsole = extendedKodein.instance<Console>(StreamType.Error)

        try {
            val applicationInfoLogger = extendedKodein.instance<ApplicationInfoLogger>()
            applicationInfoLogger.logApplicationInfo(args)

            consoleManager.enableConsoleEscapeSequences()

            val command = commandFactory.createCommand(options, extendedKodein)
            return command.run()
        } catch (e: Throwable) {
            errorConsole.println(Text.red(e.toString()))

            logger.error {
                message("Exception thrown during execution.")
                exception(e)
            }

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
