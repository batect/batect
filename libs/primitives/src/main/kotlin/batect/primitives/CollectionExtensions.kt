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

package batect.primitives

inline fun <T, V> Iterable<T>.mapToSet(transform: (T) -> V): Set<V> =
    this.mapTo(mutableSetOf(), transform)

inline fun <K, V, R> Map<K, V>.mapToSet(transform: (Map.Entry<K, V>) -> R): Set<R> =
    this.mapTo(mutableSetOf(), transform)

inline fun <T, R> Iterable<T>.flatMapToSet(transform: (T) -> Set<R>): Set<R> =
    this.fold(emptySet()) { accumulated, item ->
        accumulated + transform(item)
    }

inline fun <T> Iterable<T>.filterToSet(predicate: (T) -> Boolean): Set<T> =
    this.filterTo(mutableSetOf(), predicate)
