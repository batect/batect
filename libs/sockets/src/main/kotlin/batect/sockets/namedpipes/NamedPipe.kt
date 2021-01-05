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

package batect.sockets.namedpipes

import jnr.posix.HANDLE

internal class NamedPipe(val handle: HANDLE, internal val nativeMethods: NativeMethods) : AutoCloseable {
    override fun close() = nativeMethods.closeNamedPipe(this)

    fun write(buffer: ByteArray, offset: Int, length: Int) =
        nativeMethods.writeToNamedPipe(this, buffer, offset, length)

    fun read(buffer: ByteArray, offset: Int, maxLength: Int, timeoutInMilliseconds: Int): Int =
        nativeMethods.readFromNamedPipe(this, buffer, offset, maxLength, timeoutInMilliseconds)
}
