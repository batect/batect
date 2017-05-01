package decompose

data class ExecutionException(override val message: String, override val cause: Throwable? = null) : Exception(message, cause)
