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

import com.hypirion.io.RevivableInputStream
import okio.Buffer
import okio.Source
import okio.Timeout

data class RevivableSource(private val revivable: RevivableInputStream) : Source {
    override fun close() {
        revivable.kill()
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesToRead = byteCount.toInt()
        val array = ByteArray(bytesToRead)

        val bytesRead = revivable.read(array, 0, bytesToRead)

        if (bytesRead == -1) {
            return -1
        }

        sink.write(array, 0, bytesRead)

        return bytesRead.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE
}
