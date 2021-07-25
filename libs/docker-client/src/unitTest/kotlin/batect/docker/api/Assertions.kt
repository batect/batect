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

package batect.docker.api

import batect.docker.Json
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.connection.RealConnectionPool
import okio.Buffer
import org.mockito.kotlin.argThat

internal fun hasScheme(expectedScheme: String) = has(HttpUrl::schemeValue, equalTo(expectedScheme))
internal fun hasHost(expectedHost: String) = has(HttpUrl::hostValue, equalTo(expectedHost))
internal fun hasPath(expectedPath: String) = has(HttpUrl::encodedPathValue, equalTo(expectedPath))

// These exist to resolve the ambiguity when referring to deprecated methods and their new property counterparts above.
private fun HttpUrl.schemeValue() = this.scheme
private fun HttpUrl.hostValue() = this.host
private fun HttpUrl.encodedPathValue() = this.encodedPath

internal fun hasQueryParameter(key: String, expectedValue: String) = object : Matcher<HttpUrl> {
    override fun invoke(actual: HttpUrl): MatchResult {
        val actualParameterValues = actual.queryParameterValues(key)

        if (actualParameterValues.isEmpty()) {
            return MatchResult.Mismatch("'$actual' does not have query parameter '$key'")
        }

        if (actualParameterValues.contains(expectedValue)) {
            return MatchResult.Match
        } else if (actualParameterValues.size == 1) {
            return MatchResult.Mismatch("'$actual' has query parameter '$key' with value '${actualParameterValues.single()}'")
        } else {
            return MatchResult.Mismatch("'$actual' has query parameter '$key' with values '$actualParameterValues'")
        }
    }

    override val description: String
        get() = "has query parameter '$key' with value '$expectedValue'"
}

internal fun doesNotHaveQueryParameter(key: String) = object : Matcher<HttpUrl> {
    override fun invoke(actual: HttpUrl): MatchResult {
        val actualParameterValues = actual.queryParameterValues(key)

        if (actualParameterValues.isEmpty()) {
            return MatchResult.Match
        }

        if (actualParameterValues.size == 1) {
            return MatchResult.Mismatch("'$actual' has query parameter '$key' with value '${actualParameterValues.single()}'")
        } else {
            return MatchResult.Mismatch("'$actual' has query parameter '$key' with values '$actualParameterValues'")
        }
    }

    override val description: String
        get() = "does not have query parameter '$key'"
}

internal fun requestWithJsonBody(predicate: (JsonObject) -> Unit) = org.mockito.kotlin.check<Request> { request ->
    assertThat(request.body!!.contentType(), equalTo("application/json; charset=utf-8".toMediaType()))

    val buffer = Buffer()
    request.body!!.writeTo(buffer)
    val parsedBody = Json.default.parseToJsonElement(buffer.readUtf8()).jsonObject
    predicate(parsedBody)
}

internal fun requestWithBody(expectedBody: RequestBody) = org.mockito.kotlin.check<Request> { request ->
    assertThat(request.body, equalTo(expectedBody))
}

internal fun requestWithEmptyBody() = org.mockito.kotlin.check<Request> { request ->
    assertThat(request.body?.contentLength(), equalTo(0))
}

// HACK: ConnectionPool doesn't expose the keep-alive time, so we have to reach into it to verify that we've set it correctly.
internal fun connectionPoolWithNoEviction(): ConnectionPool = argThat {
    val delegate = ConnectionPool::class.java.getDeclaredField("delegate")
    delegate.isAccessible = true

    val keepAliveDuration = RealConnectionPool::class.java.getDeclaredField("keepAliveDurationNs")
    keepAliveDuration.isAccessible = true

    keepAliveDuration.getLong(delegate.get(this)) == Long.MAX_VALUE
}
