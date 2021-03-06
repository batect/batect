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

package batect.docker.build.buildkit.services

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireRpc
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

interface ServiceWithEndpointMetadata {
    fun getEndpoints(): Map<String, Endpoint<*, *>>

    val KFunction<*>.path: String
        get() = this.findAnnotation<WireRpc>()!!.path
}

class Endpoint<RequestType : Any, ResponseType : Any>(
    val method: (RequestType) -> ResponseType,
    val requestAdaptor: ProtoAdapter<RequestType>,
    val responseAdaptor: ProtoAdapter<ResponseType>
)
