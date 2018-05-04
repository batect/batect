/*
   Copyright 2017-2018 Charles Korn.

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

import batect.cli.CommandLineOptionsParser
import batect.cli.CommandLineOptionsParsingResult
import batect.cli.commands.CommandFactory
import batect.logging.ApplicationInfoLogger
import org.kodein.di.DKodein
import org.kodein.di.DKodeinAware
import org.kodein.di.generic.instance
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application(System.out, System.err).run(args.toList())
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
        exitProcess(-1)
    }
}

class Application(override val dkodein: DKodein) : DKodeinAware {
    constructor(outputStream: PrintStream, errorStream: PrintStream) :
        this(createKodeinConfiguration(outputStream, errorStream))

    private val errorStream: PrintStream = instance(PrintStreamType.Error)
    private val commandLineOptionsParser: CommandLineOptionsParser = instance()
    private val commandFactory: CommandFactory = instance()

    fun run(args: Iterable<String>): Int {
        try {
            val result = commandLineOptionsParser.parse(args)

            when (result) {
                is CommandLineOptionsParsingResult.Failed -> {
                    errorStream.println(result.message)
                    return -1
                }
                is CommandLineOptionsParsingResult.Succeeded -> {
                    val extendedKodein = result.options.extend(dkodein)

                    val applicationInfoLogger = extendedKodein.instance<ApplicationInfoLogger>()
                    applicationInfoLogger.logApplicationInfo(args)

                    val command = commandFactory.createCommand(result.options, extendedKodein)
                    return command.run()
                }
            }
        } catch (e: Throwable) {
            errorStream.println(e)
            return -1
        }
    }
}
