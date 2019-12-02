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

package batect.docker

import okhttp3.internal.tls.OkHostnameVerifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

sealed class DockerTLSConfig {
    abstract val scheme: String
    abstract val sslSocketFactory: SSLSocketFactory
    abstract val trustManager: X509TrustManager
    abstract val hostnameVerifier: HostnameVerifier

    object DisableTLS : DockerTLSConfig() {
        override val scheme: String = "http"
        override val hostnameVerifier: HostnameVerifier = OkHostnameVerifier.INSTANCE

        // The code for the following is taken from the documentation for OkHttpClient.Builder.sslSocketFactory().
        override val trustManager by lazy {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)

            trustManagerFactory.trustManagers.single() as X509TrustManager
        }

        override val sslSocketFactory: SSLSocketFactory by lazy {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), null)
            sslContext.socketFactory
        }
    }

    class EnableTLS(
        private val verifyServer: Boolean,
        private val caCertificatePath: Path,
        private val clientCertificatePath: Path,
        private val clientKeyPath: Path
    ) : DockerTLSConfig() {
        override val scheme: String = "https"

        override val hostnameVerifier: HostnameVerifier by lazy {
            if (verifyServer) {
                OkHostnameVerifier.INSTANCE
            } else {
                HostnameVerifier { _, _ -> true }
            }
        }

        private val keyStorePassword = "this-password-doesnt-matter".toCharArray()

        // This is based on the example at https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/kt/CustomTrust.kt
        // and https://github.com/spotify/docker-client/blob/master/src/main/java/com/spotify/docker/client/DockerCertificates.java.
        override val trustManager by lazy {
            val certificates = readCertificates(caCertificatePath)
            val keyStore = newEmptyKeyStore(keyStorePassword)

            certificates.forEach { certificate ->
                val alias = certificate.subjectX500Principal.name
                keyStore.setCertificateEntry(alias, certificate)
            }

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, keyStorePassword)

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)

            val trustManagers = trustManagerFactory.trustManagers

            check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
                "Expected exactly one trust manager with type X.509, but got: $trustManagers"
            }

            trustManagers[0] as X509TrustManager
        }

        private val clientKeyManager: KeyManager by lazy {
            val clientKey = readPrivateKey(clientKeyPath)
            val clientCerts = readCertificates(clientCertificatePath)
            val keyStore = newEmptyKeyStore(keyStorePassword)
            keyStore.setKeyEntry("key", clientKey, keyStorePassword, clientCerts)

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, keyStorePassword)
            keyManagerFactory.keyManagers.single()
        }

        private fun readPrivateKey(keyPath: Path): PrivateKey {
            Files.newBufferedReader(keyPath).use { reader ->
                PEMParser(reader).use { parser ->
                    val readObject = parser.readObject()

                    return when (readObject) {
                        is PEMKeyPair -> generatePrivateKey(keyPath, readObject.privateKeyInfo)
                        is PrivateKeyInfo -> generatePrivateKey(keyPath, readObject)
                        else -> throw IllegalArgumentException("Could not read private key from path $keyPath: received unexpected type ${readObject.javaClass.name}")
                    }
                }
            }
        }

        private fun generatePrivateKey(keyPath: Path, privateKeyInfo: PrivateKeyInfo): PrivateKey {
            val spec = PKCS8EncodedKeySpec(privateKeyInfo.encoded)
            val algorithms = listOf("RSA", "EC")

            val errors = algorithms.map { algorithm ->
                try {
                    val factory = KeyFactory.getInstance(algorithm)
                    return factory.generatePrivate(spec)
                } catch (ex: InvalidKeySpecException) {
                    algorithm to ex
                } catch (ex: NoSuchAlgorithmException) {
                    algorithm to ex
                }
            }

            throw IllegalArgumentException("Could not parse private key from $keyPath with any of $algorithms. $errors")
        }

        // The Spotify library uses the built-in Java CertificateFactory, but I found that doesn't work (it incorrectly loads
        // the certificates). Wireshark and the OpenSSL command line utility (both viewing certs and creating an encrypted connect)
        // were very helpful for debugging this.
        private fun readCertificates(certificatePath: Path): Array<X509Certificate> {
            val certs = mutableListOf<X509Certificate>()
            val converter = JcaX509CertificateConverter()

            Files.newBufferedReader(certificatePath).use { reader ->
                PEMParser(reader).use { parser ->
                    when (val obj = parser.readObject()) {
                        is X509CertificateHolder -> certs.add(converter.getCertificate(obj))
                        else -> throw IllegalArgumentException("Expected to read a certificate from $certificatePath, but got a ${obj.javaClass.name}")
                    }
                }
            }

            return certs.toTypedArray()
        }

        private fun newEmptyKeyStore(password: CharArray): KeyStore {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, password)
            return keyStore
        }

        override val sslSocketFactory: SSLSocketFactory by lazy {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(arrayOf(clientKeyManager), arrayOf(trustManager), null)
            sslContext.socketFactory
        }
    }
}
