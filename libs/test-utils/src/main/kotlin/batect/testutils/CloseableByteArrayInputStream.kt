/*
    Copyright 2017-2022 Charles Korn.

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

package batect.testutils

import java.io.ByteArrayInputStream

open class CloseableByteArrayInputStream(buf: ByteArray, offset: Int, length: Int) : ByteArrayInputStream(buf, offset, length) {
    constructor(buf: ByteArray) : this(buf, 0, buf.size)

    var isClosed: Boolean = false
        private set

    override fun close() {
        isClosed = true
        super.close()
    }
}
