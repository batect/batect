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

package batect.testutils

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

fun OkHttpClient.mockGet(url: String, responseBody: String, statusCode: Int = 200, headers: Headers = Headers.Builder().build()): Call = mock("GET", url, responseBody, statusCode, headers)
fun OkHttpClient.mockPost(url: String, responseBody: String, statusCode: Int = 200, headers: Headers = Headers.Builder().build()): Call = mock("POST", url, responseBody, statusCode, headers)
fun OkHttpClient.mockDelete(url: String, responseBody: String, statusCode: Int = 200, headers: Headers = Headers.Builder().build()): Call = mock("DELETE", url, responseBody, statusCode, headers)

fun OkHttpClient.mock(method: String, url: String, responseBody: String, statusCode: Int = 200, headers: Headers = Headers.Builder().build()): Call {
    val parsedUrl = url.toHttpUrl()

    return this.mock(method, equalTo(parsedUrl), responseBody, statusCode, headers)
}

fun OkHttpClient.mock(method: String, urlMatcher: Matcher<HttpUrl>, responseBody: String, statusCode: Int = 200, headers: Headers = Headers.Builder().build()): Call {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val parsedResponseBody = responseBody.toResponseBody(jsonMediaType)
    val call = mock<Call>()

    whenever(this.newCall(argThat { urlMatcher.invoke(url) == MatchResult.Match })).then { invocation ->
        val request = invocation.getArgument<Request>(0)

        assertThat(request, has(Request::methodValue, equalTo(method)))
        assertThat(request, has(Request::urlValue, urlMatcher))
        assertThat(request, has(Request::headerSet, equalTo(headers)))

        whenever(call.execute()).doReturn(
            Response.Builder()
                .request(request)
                .body(parsedResponseBody)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("Something happened")
                .build()
        )

        call
    }

    return call
}

fun OkHttpClient.mock(method: String, url: String, response: Response, headers: Headers = Headers.Builder().build()): Call {
    val parsedUrl = url.toHttpUrl()
    val call = mock<Call>()

    whenever(this.newCall(argThat { this.url.toString() == url })).then { invocation ->
        val request = invocation.getArgument<Request>(0)

        assertThat(request, has(Request::methodValue, equalTo(method)))
        assertThat(request, has(Request::urlValue, equalTo(parsedUrl)))
        assertThat(request, has(Request::headerSet, equalTo(headers)))

        whenever(call.execute()).doReturn(response)

        call
    }

    return call
}

// These exist to resolve the ambiguity when referring to deprecated methods and their new property counterparts above.
private fun Request.headerSet() = this.headers
private fun Request.urlValue() = this.url
private fun Request.methodValue() = this.method
