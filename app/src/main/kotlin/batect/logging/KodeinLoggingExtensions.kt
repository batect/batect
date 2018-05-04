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

package batect.logging

import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.bindings.NoArgSimpleBindingKodein
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton

inline fun <EC, BC, reified T : Any> Kodein.BindBuilder.WithScope<EC, BC>.singletonWithLogger(noinline creator: NoArgSimpleBindingKodein<BC>.(Logger) -> T) = singleton {
    creator(logger<T>())
}

inline fun <reified T : Any> DKodein.logger(): Logger {
    return this.instance<LoggerFactory>().createLoggerForClass(T::class)
}
