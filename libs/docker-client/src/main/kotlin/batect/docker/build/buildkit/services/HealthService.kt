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

package batect.docker.build.buildkit.services

import batect.docker.build.buildkit.GrpcEndpoint
import batect.docker.build.buildkit.rpcPath
import com.squareup.wire.MessageSink
import io.grpc.health.v1.HealthBlockingServer
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse

class HealthService : HealthBlockingServer {
    override fun Check(request: HealthCheckRequest): HealthCheckResponse {
        return HealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)
    }

    override fun Watch(request: HealthCheckRequest, response: MessageSink<HealthCheckResponse>) {
        throw UnsupportedGrpcMethodException(HealthBlockingServer::Watch.rpcPath)
    }

    fun getEndpoints(): Map<String, GrpcEndpoint<*, *, *>> {
        return mapOf(
            HealthBlockingServer::Check.rpcPath to GrpcEndpoint(this, HealthService::Check, HealthCheckRequest.ADAPTER, HealthCheckResponse.ADAPTER),
            HealthBlockingServer::Watch.rpcPath to GrpcEndpoint(this, HealthService::Watch, HealthCheckRequest.ADAPTER, HealthCheckResponse.ADAPTER)
        )
    }
}
