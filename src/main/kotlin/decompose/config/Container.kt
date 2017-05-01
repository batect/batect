package decompose.config

data class Container(
        val name: String,
        val buildDirectory: String,
        val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String? = null,
        val volumeMounts: Set<VolumeMount> = emptySet(),
        val portMappings: Set<PortMapping> = emptySet())
