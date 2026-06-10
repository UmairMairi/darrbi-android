package com.mytm.darrbi.core.common

/** A non-business failure of a network call (the call could not complete or could not be parsed). */
sealed class AppError(open val message: String?) {
    data class Network(override val message: String? = null) : AppError(message)
    data class Timeout(override val message: String? = null) : AppError(message)
    data class Server(val code: Int, override val message: String? = null) : AppError(message)
    data class Unauthorized(override val message: String? = null) : AppError(message)
    data class Serialization(override val message: String? = null) : AppError(message)
    data class Unknown(override val message: String? = null) : AppError(message)
}
