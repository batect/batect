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

package batect.docker.build

import batect.docker.DockerException

// Based on https://github.com/docker/engine/blob/master/pkg/fileutils/fileutils_test.go,
// https://golang.org/pkg/path/filepath/#Match and https://docs.docker.com/engine/reference/builder/#dockerignore-file.
data class ImageBuildIgnoreEntry(val pattern: String, val inverted: Boolean) {
    private val regex = buildRegex()

    private fun buildRegex(): Regex {
        val anythingExceptPathSeparator = "[^/]"
        val patternToUse = cleanPattern()
        var currentIndex = 0
        val builder = StringBuilder("^")

        while (currentIndex < patternToUse.length) {
            when (val nextChar = patternToUse[currentIndex]) {
                '?' -> {
                    builder.append(anythingExceptPathSeparator)
                }
                '*' -> {
                    val firstFollowingChar = patternToUse.getOrNull(currentIndex + 1)
                    val secondFollowingChar = patternToUse.getOrNull(currentIndex + 2)

                    if (firstFollowingChar == '*') { // Token is **
                        currentIndex++

                        if (secondFollowingChar == '/') { // Token is **/
                            currentIndex++
                        }

                        if (secondFollowingChar == null) {
                            builder.append(".*")
                        } else {
                            builder.append("(.*/)?")
                        }
                    } else {
                        builder.append(anythingExceptPathSeparator + "*")
                    }
                }
                '\\' -> {
                    val followingChar = patternToUse.getOrNull(currentIndex + 1) ?: throw invalidPattern()

                    currentIndex++

                    builder.append(Regex.escape(followingChar.toString()))
                }
                '[' -> {
                    currentIndex += buildCharacterRangeRegex(patternToUse.substring(currentIndex + 1), builder)
                }
                else -> builder.append(Regex.escape(nextChar.toString()))
            }

            currentIndex++
        }

        builder.append("(/.*)?$")

        return builder.toString().toRegex()
    }

    private fun buildCharacterRangeRegex(rangePattern: String, builder: StringBuilder): Int {
        builder.append('[')

        if (rangePattern.isEmpty()) {
            throw invalidPattern()
        }

        val firstChar = rangePattern[0]
        var currentIndex = 0

        if (firstChar == '^') {
            builder.append('^')
            currentIndex++
        }

        var isEmptyRange = true

        while (currentIndex < rangePattern.length) {
            val currentChar = rangePattern[currentIndex]
            val nextChar = rangePattern.getOrNull(currentIndex + 1)

            if (currentChar == ']') {
                if (isEmptyRange) {
                    throw invalidPattern()
                }

                builder.append(']')
                return currentIndex + 1
            }

            if (currentChar == '\\') {
                if (nextChar == null) {
                    throw invalidPattern()
                }

                builder.append(Regex.escape(nextChar.toString()))
                currentIndex++
            } else if (currentChar == '-') {
                throw invalidPattern()
            } else if (nextChar == '-') {
                if (currentIndex + 2 > rangePattern.length - 1) {
                    throw invalidPattern()
                }

                builder.append(Regex.escape(currentChar.toString()))
                builder.append('-')
                builder.append(Regex.escape(rangePattern[currentIndex + 2].toString()))
                currentIndex += 2
            } else {
                builder.append(Regex.escape(currentChar.toString()))
            }

            currentIndex++
            isEmptyRange = false
        }

        throw invalidPattern()
    }

    private fun cleanPattern(): String {
        val trimmedPattern = pattern.trim()

        return if (trimmedPattern.endsWith("/")) {
            trimmedPattern.substring(0, trimmedPattern.length - 1)
        } else {
            trimmedPattern
        }
    }

    private fun invalidPattern() = DockerException("The .dockerignore pattern '$pattern' is invalid.")

    fun matches(pathToTest: String): MatchResult = when {
        // See note at end of section at https://docs.docker.com/engine/reference/builder/#dockerignore-file:
        // "For historical reasons, the pattern . is ignored."
        pattern == "." -> MatchResult.NoMatch
        !regex.matches(pathToTest) -> MatchResult.NoMatch
        inverted -> MatchResult.MatchedInclude
        else -> MatchResult.MatchedExclude
    }

    companion object {
        fun withUncleanPattern(uncleanPattern: String, inverted: Boolean) = ImageBuildIgnoreEntry(cleanPattern(uncleanPattern), inverted)

        // This needs to match the behaviour of Golang's filepath.Clean().
        // The only difference is paths that start with a leading / will have this removed in the cleaned version.
        fun cleanPattern(pattern: String): String {
            val normalisedPattern = pattern
                .split("/")
                .filterNot { it == "" }
                .filterNot { it == "." }
                .fold(emptyList<String>()) { soFar, nextSegment ->
                    if (nextSegment != "..") {
                        soFar + nextSegment
                    } else if (soFar.isEmpty()) {
                        if (pattern.startsWith("/")) {
                            emptyList()
                        } else {
                            listOf(nextSegment)
                        }
                    } else if (soFar.last() == "..") {
                        soFar + nextSegment
                    } else {
                        soFar.dropLast(1)
                    }
                }
                .joinToString("/")

            if (normalisedPattern.isEmpty()) {
                return "."
            }

            return normalisedPattern
        }
    }
}

enum class MatchResult {
    NoMatch,
    MatchedExclude,
    MatchedInclude
}
