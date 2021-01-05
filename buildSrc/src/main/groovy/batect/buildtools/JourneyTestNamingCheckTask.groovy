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

package batect.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class JourneyTestNamingCheckTask extends DefaultTask {
    private static final Set<String> allowedSuffixes = ["JourneyTest.kt", "JourneyTests.kt"]

    @InputFiles
    final Property<SourceDirectorySet> files

    @Input
    @Optional
    final Property<FileCollection> ignoreFileNameCheck

    @OutputFile
    final Property<File> upToDateFile

    JourneyTestNamingCheckTask() {
        files = project.objects.property(SourceDirectorySet.class)
        ignoreFileNameCheck = project.objects.property(FileCollection.class)
        upToDateFile = project.objects.property(File.class)

        upToDateFile.set(temporaryDir.toPath().resolve("upToDate").toFile())
    }

    @TaskAction
    def run() {
        def filesToCheck = files.get().files

        filesToCheck.forEach { file ->
            checkFile(file)
        }

        upToDateFile.get().text = System.currentTimeMillis()
    }

    def checkFile(File file) {
        if (!endsWithAllowedSuffix(file)) {
            if (ignoreFileNameCheck.present && ignoreFileNameCheck.get().contains(file)) {
                return
            }

            throw new JourneyTestNamingException("Expected file ${file} to have a name ending with any of $allowedSuffixes.")
        }
    }

    static Boolean endsWithAllowedSuffix(File file) {
        return allowedSuffixes.any {file.name.endsWith(it) }
    }

    class JourneyTestNamingException extends RuntimeException {
        JourneyTestNamingException(String message) {
            super(message)
        }
    }
}
