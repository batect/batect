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

package batect.config.includes

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider

@Serializable
data class GitRepositoryReference(val remote: String, val ref: String) {
    val cacheKey: String by lazy {
        val digestInstance = MessageDigest.getInstance("SHA-512/224", BouncyCastleProvider())
        val data = "$remote @$ref".toByteArray(Charsets.UTF_8)
        val digest = digestInstance.digest(data)

        digest.toByteString().base64Url().trimEnd('=')
    }
}
