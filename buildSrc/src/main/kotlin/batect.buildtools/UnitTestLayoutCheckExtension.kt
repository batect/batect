package batect.buildtools

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property

interface UnitTestLayoutCheckExtension {
    val ignoreFileNameCheck: Property<FileCollection>
    val ignoreMissingMainFile: Property<FileCollection>
}
