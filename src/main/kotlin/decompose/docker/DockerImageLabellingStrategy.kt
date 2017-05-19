package decompose.docker

import decompose.config.Container

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
