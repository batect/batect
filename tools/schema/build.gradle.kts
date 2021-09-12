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

// This is all based on https://github.com/sylvainlaurent/yaml-json-validator-maven-plugin.

import batect.buildtools.GoogleCloudStorageUpload
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.fge.jsonschema.core.load.Dereferencing
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.report.LogLevel
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import java.io.IOException

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        val jacksonVersion: String by project
        val jsonSchemaValidatorVersion: String by project

        classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
        classpath("com.github.java-json-tools:json-schema-validator:$jsonSchemaValidatorVersion")
    }
}

fun loadSchema(schemaFile: File): JsonSchema {
    val loadingConfiguration = LoadingConfiguration.newBuilder()
        .dereferencing(Dereferencing.INLINE).freeze()
    val factory = JsonSchemaFactory.newBuilder()
        .setLoadingConfiguration(loadingConfiguration).freeze()

    val schema = ObjectMapper().readTree(schemaFile)

    val schemaValidationReport = factory.syntaxValidator.validateSchema(schema)
    printReport(schemaValidationReport, schemaFile)

    if (!schemaValidationReport.isSuccess) {
        throw RuntimeException("The schema $schemaFile is not valid.")
    }

    return factory.getJsonSchema(schema)
}

fun validateFileAgainstSchema(fileToValidate: File, schema: JsonSchema): Boolean =
    try {
        val yamlMapper = ObjectMapper(YAMLFactory())
        val parsedFile = yamlMapper.readTree(fileToValidate)

        val report = schema.validate(parsedFile)
        printReport(report, fileToValidate)

        report.isSuccess
    } catch (e: IOException) {
        logger.error("Error: $fileToValidate: could not validate file due to exception: $e")

        false
    }

private val schemaLogLevelsToGradleLogLevels = mapOf(
    LogLevel.DEBUG to org.gradle.api.logging.LogLevel.DEBUG,
    LogLevel.ERROR to org.gradle.api.logging.LogLevel.ERROR,
    LogLevel.FATAL to org.gradle.api.logging.LogLevel.ERROR,
    LogLevel.INFO to org.gradle.api.logging.LogLevel.INFO,
    LogLevel.NONE to org.gradle.api.logging.LogLevel.INFO,
    LogLevel.WARNING to org.gradle.api.logging.LogLevel.WARN
)

fun printReport(report: ProcessingReport, sourceFile: File) {
    report.forEach {
        logger.log(schemaLogLevelsToGradleLogLevels[it.logLevel], "${it.logLevel}: $sourceFile: $it")
    }
}

tasks.register("validateSampleFilesAgainstSchema") {
    description = "Checks that all sample configuration files are acceptable according to the schema."
    group = "Verification"

    val schemaFile = File("$projectDir/configSchema.json")
    val configFiles = fileTree("${rootProject.projectDir}/app/src/journeyTest/resources") {
        include("**/*.yml")
        exclude("**/batect.local.yml")
    } + fileTree("$projectDir/tests") {
        include("**/*.yml")
    }

    inputs.file(schemaFile)
    inputs.files(configFiles)

    doLast {
        val schema = loadSchema(schemaFile)
        val failingFiles = configFiles.filterNot { validateFileAgainstSchema(it, schema) }

        if (failingFiles.isNotEmpty()) {
            throw RuntimeException("Schema validation failed for ${failingFiles.size} file(s).")
        }
    }

    val upToDateFile = temporaryDir.resolve("lastRun")
    outputs.file(upToDateFile)

    doLast {
        // Based on https://stackoverflow.com/questions/1406473/simulate-touch-command-with-java
        upToDateFile.writeText(System.currentTimeMillis().toString())
    }
}

tasks.register("check") {
    description = "Runs all checks."
    group = "Verification"

    dependsOn("validateSampleFilesAgainstSchema")
}

tasks.register<GoogleCloudStorageUpload>("uploadConfigSchema") {
    description = "Uploads config schema file to publicly-accessible location."
    group = "Distribution"

    sourceFile.set(file("configSchema.json"))
    projectId.set("batect-ide-integration-prod")
    bucket.set("batect-ide-integration-prod-public")
    destinationPath.set("v1/configSchema.json")
}
