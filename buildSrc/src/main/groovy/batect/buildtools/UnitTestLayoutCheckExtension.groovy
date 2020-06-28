package batect.buildtools

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property

class UnitTestLayoutCheckExtension {
    Property<FileCollection> ignoreFileNameCheck
    Property<FileCollection> ignoreMissingMainFile

    UnitTestLayoutCheckExtension(Project project) {
        ignoreFileNameCheck = project.objects.property(FileCollection.class)
        ignoreMissingMainFile = project.objects.property(FileCollection.class)
    }
}
