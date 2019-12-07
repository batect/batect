/*
   Copyright 2017-2019 Charles Korn.

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

import okhttp3.Interceptor
import okhttp3.Response

class HttpLoggingInterceptor(private val logger: Logger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        logger.debug {
            message("HTTP request starting.")
            data("url", request.url.toString())
            data("method", request.method)
            data("contentLength", request.body?.contentLength() ?: 0)
            data("contentType", request.body?.contentType()?.toString())
        }

        val response = chain.proceed(request)

        logger.debug {
            message("HTTP response received.")
            data("url", request.url.toString())
            data("method", request.method)
            data("code", response.code)
            data("message", response.message)
            data("contentLength", response.body?.contentLength() ?: 0)
            data("contentType", response.body?.contentType()?.toString())
        }

        return response
    }
}
