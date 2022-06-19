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

package batect.docker.build.buildkit

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireRpc
import com.squareup.wire.internal.GrpcMessageSink
import com.squareup.wire.internal.GrpcMessageSource
import okhttp3.Headers
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

typealias ServiceInstanceFactory<T> = (Headers) -> T
typealias EndpointMethod<ServiceType, RequestType, ResponseType> = (ServiceType, GrpcMessageSource<RequestType>, GrpcMessageSink<ResponseType>) -> Unit

class GrpcEndpoint<ServiceType : Any, RequestType : Any, ResponseType : Any>(
    val serviceInstanceFactory: ServiceInstanceFactory<ServiceType>,
    val method: EndpointMethod<ServiceType, RequestType, ResponseType>,
    val requestAdaptor: ProtoAdapter<RequestType>,
    val responseAdaptor: ProtoAdapter<ResponseType>
) {
    constructor (
        serviceInstanceFactory: ServiceInstanceFactory<ServiceType>,
        method: (ServiceType, RequestType) -> ResponseType,
        requestAdaptor: ProtoAdapter<RequestType>,
        responseAdaptor: ProtoAdapter<ResponseType>
    ) :
        this(serviceInstanceFactory, unaryRequestUnaryResponseAdaptor(method), requestAdaptor, responseAdaptor)

    constructor (
        serviceInstance: ServiceType,
        method: (ServiceType, RequestType) -> ResponseType,
        requestAdaptor: ProtoAdapter<RequestType>,
        responseAdaptor: ProtoAdapter<ResponseType>
    ) :
        this({ serviceInstance }, unaryRequestUnaryResponseAdaptor(method), requestAdaptor, responseAdaptor)

    constructor (
        serviceInstance: ServiceType,
        method: (ServiceType, RequestType, GrpcMessageSink<ResponseType>) -> Unit,
        requestAdaptor: ProtoAdapter<RequestType>,
        responseAdaptor: ProtoAdapter<ResponseType>
    ) :
        this({ serviceInstance }, unaryRequestStreamingResponseAdaptor(method), requestAdaptor, responseAdaptor)

    companion object {
        private fun <ServiceType : Any, RequestType : Any, ResponseType : Any> unaryRequestUnaryResponseAdaptor(method: (ServiceType, RequestType) -> ResponseType): EndpointMethod<ServiceType, RequestType, ResponseType> {
            return { serviceInstance, source, sink ->
                val request = source.readExactlyOneAndClose()
                val response = method.invoke(serviceInstance, request)
                sink.write(response)
            }
        }

        private fun <ServiceType : Any, RequestType : Any, ResponseType : Any> unaryRequestStreamingResponseAdaptor(method: (ServiceType, RequestType, GrpcMessageSink<ResponseType>) -> Unit): EndpointMethod<ServiceType, RequestType, ResponseType> {
            return { serviceInstance, source, sink ->
                val request = source.readExactlyOneAndClose()
                method.invoke(serviceInstance, request, sink)
            }
        }
    }
}

val KFunction<*>.rpcPath: String
    get() = this.findAnnotation<WireRpc>()!!.path
