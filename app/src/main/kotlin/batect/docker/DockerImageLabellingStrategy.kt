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

package batect.docker

import batect.config.Container

// HACK
// Ideally this wouldn't be necessary, as we should be able to parse the output of `docker build`
// (or just use the Docker API directly) and get the image ID from that, rather than attaching a label to the image we build.
// However, if we redirect the output of `docker build`, we lose the nice progress output.
// And if we want to use the Docker API, we have to implement all that progress reporting ourselves.
// So, this is the lesser of three evils: we have to come up with some kind of label ourselves.
// In the future, we'd probably create our own Docker API client and do all the progress reporting ourselves,
// which then gives us the ability to just extract the image ID from the stream of build events.
//
// This implementation is also problematic because it will return the same label for different images
// if they have the same project name and container name (eg. two clones of the same project with different
// image contents for the corresponding container). At the moment we only use the label to create a
// container from that image immediately building it, so unless two builds are running at the same time,
// this shouldn't be a problem.
class DockerImageLabellingStrategy {
    fun labelImage(projectName: String, container: Container): String = "$projectName-${container.name}:latest"
}
