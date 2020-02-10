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

package batect.execution

import batect.cli.CommandLineOptions

data class RunOptions(
    val taskName: String,
    val additionalTaskCommandArguments: Iterable<String>,
    val behaviourAfterSuccess: CleanupOption,
    val behaviourAfterFailure: CleanupOption,
    val propagateProxyEnvironmentVariables: Boolean,
    val imageOverrides: Map<String, String>
) {
    constructor(options: CommandLineOptions) : this(
        options.taskName!!,
        options.additionalTaskCommandArguments,
        if (options.disableCleanupAfterSuccess) CleanupOption.DontCleanup else CleanupOption.Cleanup,
        if (options.disableCleanupAfterFailure) CleanupOption.DontCleanup else CleanupOption.Cleanup,
        !options.dontPropagateProxyEnvironmentVariables,
        options.imageOverrides
    )
}
