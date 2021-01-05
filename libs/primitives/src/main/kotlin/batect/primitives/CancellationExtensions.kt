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

package batect.primitives

import okhttp3.Call
import okhttp3.Response
import java.io.IOException

inline fun <R> Call.executeInCancellationContext(cancellationContext: CancellationContext, operation: (Response) -> R): R {
    cancellationContext.addCancellationCallback(this::cancel).use {
        try {
            return execute().use(operation)
        } catch (e: IOException) {
            if (isCanceled()) {
                throw CancellationException("Request was cancelled.", e)
            } else {
                throw e
            }
        }
    }
}

class CancellationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
