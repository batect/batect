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

package batect.os

import jnr.constants.platform.Signal
import jnr.posix.LibC
import jnr.posix.POSIX
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class SignalListener(private val posix: POSIX) {
    fun start(signal: Signal, handler: SignalHandler): AutoCloseable {
        val handlersForSignal = handlers.getOrPut(signal.value(), { ConcurrentLinkedDeque() })
        handlersForSignal.add(handler)

        posix.libc().signal(signal.value(), Companion)

        return AutoCloseable {
            handlers.getValue(signal.value()).remove(handler)
        }
    }

    // See https://github.com/jnr/jnr-posix/issues/137 for why this is necessary.
    companion object : LibC.LibCSignalHandler {
        private val handlers = ConcurrentHashMap<Int, ConcurrentLinkedDeque<SignalHandler>>()

        override fun signal(sig: Int) {
            handlers.getOrDefault(sig, ConcurrentLinkedDeque()).peekLast()?.invoke()
        }
    }
}

typealias SignalHandler = () -> Unit
