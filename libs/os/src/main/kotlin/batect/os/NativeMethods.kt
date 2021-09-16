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

import jnr.constants.platform.Errno
import jnr.constants.platform.windows.LastError
import jnr.posix.POSIX
import kotlin.reflect.KFunction

interface NativeMethods {
    fun getConsoleDimensions(): Dimensions
    fun determineIfStdinIsTTY(): Boolean
    fun determineIfStdoutIsTTY(): Boolean
    fun determineIfStderrIsTTY(): Boolean

    fun getUserId(): Int
    fun getGroupId(): Int

    fun getUserName(): String
    fun getGroupName(): String
}

abstract class NativeMethodException(val method: String, val errorName: String, val errorDescription: String) :
    RuntimeException("Invoking native method $method failed with error $errorName ($errorDescription).")

class UnixNativeMethodException(method: String, val error: Errno) : NativeMethodException(method, error.name, error.description())

class WindowsNativeMethodException(method: String, errorName: String, errorDescription: String, val error: LastError?) :
    NativeMethodException(method, errorName, errorDescription) {

    constructor(method: String, error: LastError) : this(method, error.name, error.toString(), error)
}

fun <R> throwWindowsNativeMethodFailed(function: KFunction<R>, posix: POSIX): Nothing = throwWindowsNativeMethodFailed(function.name, posix)

fun throwWindowsNativeMethodFailed(functionName: String, posix: POSIX): Nothing {
    val errno = posix.errno()
    val error = LastError.values().singleOrNull { it.intValue() == errno }

    if (error != null) {
        throw WindowsNativeMethodException(functionName, error)
    }

    throw WindowsNativeMethodException(functionName, "0x${errno.toString(16)}", "unknown", null)
}

class NoConsoleException : RuntimeException("STDOUT is not connected to a console.")
