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

package batect.ui.containerio

import batect.config.Container
import batect.config.SetupCommand
import batect.os.Dimensions
import batect.ui.ConsoleInfo
import com.hypirion.io.RevivableInputStream
import okio.Sink
import okio.Source
import java.io.PrintStream

data class TaskContainerOnlyIOStreamingOptions(
    private val taskContainer: Container,
    private val stdout: PrintStream,
    private val stdin: RevivableInputStream,
    private val consoleInfo: ConsoleInfo
) : ContainerIOStreamingOptions {
    override fun terminalTypeForContainer(container: Container): String? = consoleInfo.terminalType
    override val frameDimensions = Dimensions(0, 0)

    override fun stdinForContainer(container: Container): Source? {
        if (container == taskContainer) {
            stdin.resurrect()
            return RevivableSource(stdin)
        }

        return null
    }

    override fun stdoutForContainer(container: Container): Sink? {
        if (container == taskContainer) {
            return UncloseableSink(stdout)
        }

        return null
    }

    override fun stdoutForContainerSetupCommand(container: Container, setupCommand: SetupCommand, index: Int): Sink? = null
    override fun stdoutForImageBuild(container: Container): Sink? = null
}
