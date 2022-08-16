/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ui.containerio

import batect.config.Container
import batect.config.SetupCommand
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import batect.os.ConsoleInfo
import okio.Sink
import java.io.PrintStream

data class TaskContainerOnlyIOStreamingOptions(
    private val taskContainer: Container,
    private val stdout: PrintStream,
    private val consoleInfo: ConsoleInfo
) : ContainerIOStreamingOptions {
    override fun terminalTypeForContainer(container: Container): String? = consoleInfo.terminalType
    override fun attachStdinForContainer(container: Container): Boolean = container == taskContainer

    override fun stdinForContainer(container: Container): TextInput? {
        if (container == taskContainer) {
            return TextInput.StandardInput
        }

        return null
    }

    override fun stdoutForContainer(container: Container): TextOutput? {
        if (container == taskContainer) {
            return TextOutput.StandardOutput
        }

        return null
    }

    override fun stdoutForContainerSetupCommand(container: Container, setupCommand: SetupCommand, index: Int): Sink? = null
    override fun stdoutForImageBuild(container: Container): Sink? = null
    override fun useTTYForContainer(container: Container): Boolean = consoleInfo.stdoutIsTTY && container == taskContainer
}
