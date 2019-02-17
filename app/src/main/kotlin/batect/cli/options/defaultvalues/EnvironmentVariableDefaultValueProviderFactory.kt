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

package batect.cli.options.defaultvalues

class EnvironmentVariableDefaultValueProviderFactory(private val environment: Map<String, String> = System.getenv()) {
    fun create(name: String, default: String): DefaultValueProvider<String> = object : DefaultValueProvider<String> {
        override val value: String
            get() = environment.getOrDefault(name, default)

        override val description: String
            get() = "defaults to the value of the $name environment variable (which is currently $currentStateDescription) or '$default' if $name is not set"

        private val currentStateDescription = if (environment.containsKey(name)) {
            "'${environment.getValue(name)}'"
        } else {
            "not set"
        }
    }
}
