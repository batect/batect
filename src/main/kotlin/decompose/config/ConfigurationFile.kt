package decompose.config

data class ConfigurationFile(val projectName: String, val tasks: Map<String, Task> = emptyMap())
