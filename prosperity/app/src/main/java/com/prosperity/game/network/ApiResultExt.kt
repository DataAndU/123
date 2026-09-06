package com.prosperity.game.network

import com.google.gson.Gson
import com.prosperity.game.network.dto.ApiErrorBody
import retrofit2.Response

private val gson = Gson()

/** Converts a Retrofit [Response] into a [Result], surfacing the server's `{error}` JSON body as the failure message. */
fun <T> Response<T>.toResult(): Result<T> {
    if (isSuccessful) {
        val body = body()
        return if (body != null) Result.success(body) else Result.failure(IllegalStateException("Empty response body"))
    }
    val message = try {
        val errorJson = errorBody()?.string()
        errorJson?.let { gson.fromJson(it, ApiErrorBody::class.java)?.error } ?: "Request failed (${code()})"
    } catch (_: Exception) {
        "Request failed (${code()})"
    }
    return Result.failure(ApiException(message ?: "Request failed (${code()})", code()))
}

class ApiException(message: String, val httpCode: Int) : Exception(message)

/** Runs a suspending network call and converts any thrown exception (no connectivity, timeout, etc.) into a Result too. */
suspend fun <T> safeApiCall(block: suspend () -> Response<T>): Result<T> = try {
    block().toResult()
} catch (e: Exception) {
    Result.failure(e)
}
