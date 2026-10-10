package uk.hunttrack.core.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable, val message: String = throwable.message ?: "Unknown error") : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

inline fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data
