package decompose.config

data class Configuration(
        val projectName: String,
        val tasks: TaskSet,
        val containers: Map<String, Container>)
