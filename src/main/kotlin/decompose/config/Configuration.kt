package decompose.config

data class Configuration(
        val projectName: String,
        val tasks: TaskMap,
        val containers: Map<String, Container>)
