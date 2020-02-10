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

package batect.logging

import jnr.posix.POSIX
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.LongSerializer
import kotlinx.serialization.internal.StringSerializer

class StandardAdditionalDataSource(
    private val posix: POSIX,
    private val threadSource: () -> Thread = { Thread.currentThread() }
) {
    private val pid by lazy { posix.getpid() }

    fun getAdditionalData(): Map<String, Jsonable> {
        val thread = threadSource()

        return mapOf(
            "@processId" to JsonableObject(pid, IntSerializer),
            "@threadId" to JsonableObject(thread.id, LongSerializer),
            "@threadName" to JsonableObject(thread.name, StringSerializer)
        )
    }
}
