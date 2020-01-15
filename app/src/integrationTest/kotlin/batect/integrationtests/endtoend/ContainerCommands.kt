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

package batect.integrationtests.endtoend

object ContainerCommands {
    val exitImmediately = listOf("echo", "Hello from the container")

    // See https://stackoverflow.com/a/21882119/1668119 for an explanation of this - we need something that waits indefinitely but immediately responds to a SIGTERM by quitting (sh and wait don't do this).
    val waitIndefinitely = listOf("sh", "-c", "trap 'trap - TERM; kill -s TERM -$$' TERM; tail -f /dev/null & wait")
}
