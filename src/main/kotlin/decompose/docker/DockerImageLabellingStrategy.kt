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
interface DockerImageLabellingStrategy {
    fun labelImage(projectName: String, container: Container): String
}
