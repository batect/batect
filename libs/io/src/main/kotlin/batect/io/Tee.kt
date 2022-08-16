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

package batect.io

import okio.Buffer
import okio.Sink
import okio.Timeout

public class Tee(private vararg val sinks: Sink) : Sink {
    override fun close() = sinks.forEach { it.close() }
    override fun flush() = sinks.forEach { it.flush() }
    override fun write(source: Buffer, byteCount: Long) {
        sinks.forEach { it.write(source.copy(), byteCount) }

        source.skip(byteCount)
    }

    override fun timeout(): Timeout = throw UnsupportedOperationException()
}
