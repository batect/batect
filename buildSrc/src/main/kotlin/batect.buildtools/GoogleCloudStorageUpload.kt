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
import java.nio.file.Files

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
            .build()

        storage.create(blobInfo, Files.readAllBytes(sourceFile.get().asFile.toPath()))
    }
}
