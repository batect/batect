/*
   Copyright 2017-2018 Charles Korn.

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

import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

private val jsonMediaType = MediaType.get("application/json; charset=utf-8")

fun OkHttpClient.mockGet(url: String, body: String, statusCode: Int = 200) {
    val parsedUrl = HttpUrl.get(url)
    val responseBody = ResponseBody.create(jsonMediaType, body)

    whenever(this.newCall(argThat { method() == "GET" && url().equals(parsedUrl) })).then { invocation ->
        val request = invocation.getArgument<Request>(0)

        mock<Call> {
            on { execute() } doReturn (
                Response.Builder()
                    .request(request)
                    .body(responseBody)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("Something happened")
                    .build()
                )
        }
    }
}

fun OkHttpClient.mockPost(url: String, body: String, statusCode: Int = 200): Call {
    val parsedUrl = HttpUrl.get(url)
    val responseBody = ResponseBody.create(jsonMediaType, body)
    val call = mock<Call>()

    whenever(this.newCall(argThat { method() == "POST" && url().equals(parsedUrl) })).then { invocation ->
        val request = invocation.getArgument<Request>(0)

        whenever(call.execute()).doReturn(
            Response.Builder()
                .request(request)
                .body(responseBody)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("Something happened")
                .build()
        )

        call
    }

    return call
}
