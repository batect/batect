/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.buildtools

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

abstract class GoogleCloudStorageUpload : DefaultTask() {
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val projectId: Property<String>

    @get:Input
    abstract val bucket: Property<String>

    @get:Input
    abstract val destinationPath: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val environmentVariableName = "GCP_SERVICE_ACCOUNT_KEY"
        val serviceAccountKey = System.getenv(environmentVariableName) ?: throw RuntimeException("'$environmentVariableName' environment variable not set.")

        val credentials = GoogleCredentials
            .fromStream(ByteArrayInputStream(serviceAccountKey.toByteArray(Charsets.UTF_8)))
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

        val storage = StorageOptions.newBuilder()
            .setProjectId(projectId.get())
            .setCredentials(credentials)
            .build()
            .service

        val blobId = BlobId.of(bucket.get(), destinationPath.get())
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setCacheControl("public, max-age=300")
            .setContentType("application/json")
            .setContentEncoding("gzip")
            .build()

        val buffer = ByteArrayOutputStream()

        GZIPOutputStream(buffer).use { gzipStream ->
            val sourceFileBytes = Files.readAllBytes(sourceFile.get().asFile.toPath())
            gzipStream.write(sourceFileBytes)
            gzipStream.finish()
            storage.create(blobInfo, buffer.toByteArray())
        }
    }
}
