package decompose.config

data class Container(
        val name: String,
        val buildDirectory: String,
        val environment: Map<String, String>,
        val workingDirectory: String?,
        val volumeMounts: Set<VolumeMount>,
        val portMappings: Set<PortMapping>)
