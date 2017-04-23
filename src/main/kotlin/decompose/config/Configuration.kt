package decompose.config

data class Configuration(
        val projectName: String,
        val tasks: Map<String, Task>,
        val containers: Map<String, Container>)
