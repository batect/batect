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

package batect.docker.api

import batect.testutils.equalTo
import batect.utils.Json
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import java.util.UUID

internal val isUUID = Matcher(::validUUID)
internal fun validUUID(value: String): Boolean {
    try {
        UUID.fromString(value)
        return true
    } catch (_: IllegalArgumentException) {
        return false
    }
}

internal fun hasScheme(expectedScheme: String) = has(HttpUrl::scheme, equalTo(expectedScheme))
internal fun hasHost(expectedHost: String) = has(HttpUrl::host, equalTo(expectedHost))
internal fun hasPath(expectedPath: String) = has(HttpUrl::encodedPath, equalTo(expectedPath))

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

internal fun requestWithJsonBody(predicate: (JsonObject) -> Unit) = com.nhaarman.mockitokotlin2.check<Request> { request ->
    assertThat(request.body()!!.contentType(), equalTo(MediaType.get("application/json; charset=utf-8")))

    val buffer = Buffer()
    request.body()!!.writeTo(buffer)
    val parsedBody = Json.parser.parseJson(buffer.readUtf8()).jsonObject
    predicate(parsedBody)
}

internal fun requestWithBody(expectedBody: RequestBody) = com.nhaarman.mockitokotlin2.check<Request> { request ->
    assertThat(request.body(), equalTo(expectedBody))
}
