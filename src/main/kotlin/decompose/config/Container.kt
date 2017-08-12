package decompose.config

data class Container(
        val name: String,
        val buildDirectory: String,
        val command: String? = null,
        val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String? = null,
        val volumeMounts: Set<VolumeMount> = emptySet(),
        val portMappings: Set<PortMapping> = emptySet(),
        val dependencies: Set<String> = emptySet())
