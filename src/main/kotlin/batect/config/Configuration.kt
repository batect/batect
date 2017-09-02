package batect.config

data class Configuration(
        val projectName: String,
        val tasks: TaskMap,
        val containers: ContainerMap)
