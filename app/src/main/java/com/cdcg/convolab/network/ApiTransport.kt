package com.cdcg.convolab.network

import java.io.IOException

interface ApiTransport {
    suspend fun request(method: String, path: String, token: String? = null, body: String? = null): String
}

class ApiFailure(val status: Int) : IOException(messageFor(status)) {
    companion object {
        private fun messageFor(status: Int) = when (status) {
            401 -> "Please check your sign-in details or sign in again."
            403 -> "Your account cannot perform this action."
            409 -> "This item changed elsewhere. Your local changes are still available."
            422 -> "Please check the information you entered and try again."
            429 -> "Too many requests. Please wait a moment and try again."
            else -> "The service is unavailable. Please try again."
        }
    }
}

fun Throwable.userMessage(): String = when (this) {
    is ApiFailure -> message.orEmpty()
    is IOException -> "Unable to connect. Please check your connection and try again."
    else -> "The operation could not be completed. Please try again."
}
