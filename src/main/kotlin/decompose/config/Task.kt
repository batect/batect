package decompose.config

data class Task(val name: String,
                val runConfiguration: TaskRunConfiguration,
                val description: String = "",
                val dependencies: Set<String> = emptySet())

data class TaskRunConfiguration(val container: String, val command: String?)
