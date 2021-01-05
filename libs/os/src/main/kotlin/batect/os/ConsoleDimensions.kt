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

package batect.os

import batect.logging.Logger
import jnr.constants.platform.Signal
import java.util.concurrent.atomic.AtomicReference

class ConsoleDimensions(
    private val nativeMethods: NativeMethods,
    private val signalListener: SignalListener,
    private val logger: Logger
) {
    private val currentDimensions: AtomicReference<Result<Dimensions?>> = AtomicReference(Result.success(null))
    private val listeners = mutableListOf<Listener>()

    init {
        signalListener.start(Signal.SIGWINCH, ::updateCachedDimensions)

        updateCachedDimensions()
    }

    val current: Dimensions?
        get() = currentDimensions.get().getOrThrow()

    fun registerListener(listener: Listener): AutoCloseable {
        listeners.add(listener)

        return AutoCloseable { listeners.remove(listener) }
    }

    private fun updateCachedDimensions() {
        try {
            val newDimensions = nativeMethods.getConsoleDimensions()
            currentDimensions.set(Result.success(newDimensions))

            logger.info {
                message("Got console dimensions.")
                data("dimensions", newDimensions)
            }
        } catch (e: NoConsoleException) {
            logger.warn {
                message("Getting console dimensions failed because we are not running in a console.")
                exception(e)
            }

            currentDimensions.set(Result.success(null))
        } catch (e: NativeMethodException) {
            logger.warn {
                message("Getting console dimensions failed.")
                exception(e)
            }

            currentDimensions.set(Result.failure(e))
        }

        listeners.forEach { it() }
    }
}

private typealias Listener = () -> Unit
