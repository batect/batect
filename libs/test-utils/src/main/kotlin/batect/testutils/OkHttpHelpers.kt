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

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val noHeaders = Headers.Builder().build()

fun OkHttpClient.mockGet(url: String, responseBody: String, statusCode: Int = 200, responseHeaders: Headers = noHeaders): Call =
    mock("GET", url, responseBody, statusCode, responseHeaders = responseHeaders)

fun OkHttpClient.mockPost(url: String, responseBody: String, statusCode: Int = 200): Call = mock("POST", url, responseBody, statusCode)
fun OkHttpClient.mockPost(urlMatcher: Matcher<HttpUrl>, responseBody: String, statusCode: Int = 200): Call = mock("POST", urlMatcher, responseBody, statusCode)
fun OkHttpClient.mockPut(url: String, responseBody: String, statusCode: Int = 200): Call = mock("PUT", url, responseBody, statusCode)
fun OkHttpClient.mockPut(urlMatcher: Matcher<HttpUrl>, responseBody: String, statusCode: Int = 200): Call = mock("PUT", urlMatcher, responseBody, statusCode)
fun OkHttpClient.mockDelete(url: String, responseBody: String, statusCode: Int = 200): Call = mock("DELETE", url, responseBody, statusCode)
fun OkHttpClient.mockDelete(urlMatcher: Matcher<HttpUrl>, responseBody: String, statusCode: Int = 200): Call = mock("DELETE", urlMatcher, responseBody, statusCode)

fun OkHttpClient.mock(
    method: String,
    url: String,
    responseBody: String,
    statusCode: Int = 200,
    responseHeaders: Headers = noHeaders
): Call {
    val parsedUrl = url.toHttpUrl()

    return this.mock(method, equalTo(parsedUrl), responseBody, statusCode, responseHeaders)
}

fun OkHttpClient.mock(
    method: String,
    urlMatcher: Matcher<HttpUrl>,
    responseBody: String,
    statusCode: Int = 200,
    responseHeaders: Headers = noHeaders
): Call {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val parsedResponseBody = responseBody.toResponseBody(jsonMediaType)
    var requestReceived: Request? = null

    val call = mock<Call> {
        on { request() } doAnswer {
            if (requestReceived != null) {
                requestReceived
            } else {
                throw RuntimeException("Call was never invoked, can't return request.")
            }
        }
    }

    whenever(this.newCall(argThat { urlMatcher.invoke(url) == MatchResult.Match })).then { invocation ->
        val request = invocation.getArgument<Request>(0)
        requestReceived = request

        assertThat(request, has(Request::methodValue, equalTo(method)))

        whenever(call.execute()).doReturn(
            Response.Builder()
                .request(request)
                .body(parsedResponseBody)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("Something happened")
                .headers(responseHeaders)
                .build()
        )

        call
    }

    return call
}

fun OkHttpClient.mock(method: String, url: String, response: Response): Call {
    val parsedUrl = url.toHttpUrl()
    var requestReceived: Request? = null

    val call = mock<Call> {
        on { request() } doAnswer {
            if (requestReceived != null) {
                requestReceived
            } else {
                throw RuntimeException("Call was never invoked, can't return request.")
            }
        }
    }

    whenever(this.newCall(argThat { this.url == parsedUrl })).then { invocation ->
        val request = invocation.getArgument<Request>(0)
        requestReceived = request

        assertThat(request, has(Request::methodValue, equalTo(method)))

        whenever(call.execute()).doReturn(response)

        call
    }

    return call
}

// These exist to resolve the ambiguity when referring to deprecated methods and their new property counterparts above.
private fun Request.headerSet() = this.headers
private fun Request.urlValue() = this.url
private fun Request.methodValue() = this.method
