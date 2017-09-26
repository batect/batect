/*
   Copyright 2017 Charles Korn.

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

package batect.cli

import batect.logging.FileLogSink
import batect.logging.LogMessageWriter
import batect.logging.LogSink
import batect.logging.NullLogSink
import batect.logging.StandardAdditionalDataSource
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.FileSystem

object BatectCommandLineParserSpec : Spek({
    describe("a batect-specific command line parser") {
        val emptyKodein = Kodein {}

        given("no configuration file name or log file name have been provided") {
            val parser = BatectCommandLineParser(emptyKodein)
            parser.parse(emptyList())

            on("creating the set of Kodein bindings") {
                val bindings = Kodein {
                    import(parser.createBindings())
                }

                it("includes the default value for the configuration file name") {
                    assertThat(bindings.instance<String>(CommonOptions.ConfigurationFileName), equalTo("batect.yml"))
                }

                it("creates a null log sink to use") {
                    assertThat(bindings.instance<LogSink>(), isA<NullLogSink>())
                }
            }
        }

        given("a non-standard configuration file name has been provided") {
            val parser = BatectCommandLineParser(emptyKodein)
            parser.parse(listOf("--config-file", "some-other-file.yml"))

            on("creating the set of Kodein bindings") {
                val bindings = Kodein {
                    import(parser.createBindings())
                }

                it("includes the custom value for the configuration file name") {
                    assertThat(bindings.instance<String>(CommonOptions.ConfigurationFileName), equalTo("some-other-file.yml"))
                }
            }
        }

        given("a log file name has been provided") {
            val logMessageWriter = mock<LogMessageWriter>()
            val additionalDataSource = mock<StandardAdditionalDataSource>()
            val fileSystem = Jimfs.newFileSystem(Configuration.unix())

            val parser = BatectCommandLineParser(emptyKodein)
            parser.parse(listOf("--log-file", "log.log"))

            on("creating the set of Kodein bindings") {
                val bindings = Kodein {
                    bind<LogMessageWriter>() with instance(logMessageWriter)
                    bind<StandardAdditionalDataSource>() with instance(additionalDataSource)
                    bind<FileSystem>() with instance(fileSystem)

                    import(parser.createBindings())
                }

                val logSink = bindings.instance<LogSink>()

                it("creates a file log sink to use") {
                    assertThat(logSink, isA<FileLogSink>())
                }

                it("creates the file log sink with the expected file name") {
                    assertThat((logSink as FileLogSink).path, equalTo(fileSystem.getPath("log.log")))
                }
            }
        }
    }
})
