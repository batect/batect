package batect.buildtools

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property

interface JourneyTestNamingCheckExtension {
    abstract val ignoreFileNameCheck: Property<FileCollection>
}
