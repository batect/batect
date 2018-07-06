/*
   Copyright 2017-2018 Charles Korn.

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

package batect.os.proxies

import java.util.TreeSet

class ProxyEnvironmentVariablesProvider(
    private val preprocessor: ProxyEnvironmentVariablePreprocessor,
    private val hostEnvironmentVariables: Map<String, String>
) {
    constructor(preprocessor: ProxyEnvironmentVariablePreprocessor) : this(preprocessor, System.getenv())

    private val proxyEnvironmentVariableNames: Set<String> = setOf("http_proxy", "https_proxy", "ftp_proxy", "no_proxy")
        .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))

    private val proxyEnvironmentVariablesNeedingPreprocessing = setOf("http_proxy", "https_proxy", "ftp_proxy")
        .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))

    fun getProxyEnvironmentVariables(extraNoProxyEntries: Set<String>): Map<String, String> {
        val variables = hostEnvironmentVariables
            .filterKeys { name -> name in proxyEnvironmentVariableNames }
            .mapValues { (name, value) ->
                if (name in proxyEnvironmentVariablesNeedingPreprocessing) preprocessor.process(value) else value
            }

        if (variables.isEmpty() || extraNoProxyEntries.isEmpty()) {
            return variables
        }

        val noProxyVariables = mapOf(
            "no_proxy" to addExtraNoProxyEntries(variables["no_proxy"], extraNoProxyEntries),
            "NO_PROXY" to addExtraNoProxyEntries(variables["NO_PROXY"], extraNoProxyEntries)
        )

        return variables + noProxyVariables
    }

    private fun addExtraNoProxyEntries(existingValue: String?, extraNoProxyEntries: Set<String>): String {
        val extraEntries = extraNoProxyEntries.joinToString(",")

        if (existingValue == null || existingValue == "") {
            return extraEntries
        }

        return existingValue + "," + extraEntries
    }
}
