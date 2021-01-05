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

import batect.docker.build.ImageBuildOutputSink
import batect.docker.pull.PasswordRegistryCredentials
import batect.docker.pull.RegistryCredentialsProvider
import batect.docker.pull.TokenRegistryCredentials
import batect.logging.Logger
import moby.filesync.v1.AuthBlockingServer
import moby.filesync.v1.CredentialsRequest
import moby.filesync.v1.CredentialsResponse
import moby.filesync.v1.FetchTokenRequest
import moby.filesync.v1.FetchTokenResponse
import moby.filesync.v1.GetTokenAuthorityRequest
import moby.filesync.v1.GetTokenAuthorityResponse
import moby.filesync.v1.VerifyTokenAuthorityRequest
import moby.filesync.v1.VerifyTokenAuthorityResponse

// This is based on github.com/moby/buildkit/session/auth/authprovider/authprovider.go.
class AuthService(
    private val credentialsProvider: RegistryCredentialsProvider,
    private val outputSink: ImageBuildOutputSink,
    private val logger: Logger
) : AuthBlockingServer, ServiceWithEndpointMetadata {
    override fun GetTokenAuthority(request: GetTokenAuthorityRequest): GetTokenAuthorityResponse {
        // Docker defaults to calling this first, but it appears that we're able to return a gRPC UNIMPLEMENTED error for this,
        // and Docker will then revert to calling Credentials().
        throw UnsupportedGrpcMethodException(AuthBlockingServer::GetTokenAuthority.path)
    }

    override fun Credentials(request: CredentialsRequest): CredentialsResponse {
        val registry = if (request.Host == "registry-1.docker.io") "https://index.docker.io/v1/" else request.Host

        logger.info {
            message("Received request for credentials.")
            data("host", request.Host)
            data("registryToLoad", registry)
        }

        val creds = credentialsProvider.getCredentials(registry)

        if (creds == null) {
            outputSink.use { buffer -> buffer.writeUtf8("## [auth] daemon requested credentials for ${request.Host}, but none are available\n") }

            return CredentialsResponse()
        }

        outputSink.use { buffer -> buffer.writeUtf8("## [auth] daemon requested credentials for ${request.Host}\n") }

        return when (creds) {
            is PasswordRegistryCredentials -> CredentialsResponse(Username = creds.username, Secret = creds.password)
            is TokenRegistryCredentials -> CredentialsResponse(Secret = creds.identityToken)
        }
    }

    override fun FetchToken(request: FetchTokenRequest): FetchTokenResponse {
        // This doesn't appear to be used.
        throw UnsupportedGrpcMethodException(AuthBlockingServer::FetchToken.path)
    }

    override fun VerifyTokenAuthority(request: VerifyTokenAuthorityRequest): VerifyTokenAuthorityResponse {
        // This doesn't appear to be used.
        throw UnsupportedGrpcMethodException(AuthBlockingServer::VerifyTokenAuthority.path)
    }

    override fun getEndpoints(): Map<String, Endpoint<*, *>> {
        return mapOf(
            AuthBlockingServer::Credentials.path to Endpoint(::Credentials, CredentialsRequest.ADAPTER, CredentialsResponse.ADAPTER),
            AuthBlockingServer::FetchToken.path to Endpoint(::FetchToken, FetchTokenRequest.ADAPTER, FetchTokenResponse.ADAPTER),
            AuthBlockingServer::GetTokenAuthority.path to Endpoint(::GetTokenAuthority, GetTokenAuthorityRequest.ADAPTER, GetTokenAuthorityResponse.ADAPTER),
            AuthBlockingServer::VerifyTokenAuthority.path to Endpoint(::VerifyTokenAuthority, VerifyTokenAuthorityRequest.ADAPTER, VerifyTokenAuthorityResponse.ADAPTER),
        )
    }
}
