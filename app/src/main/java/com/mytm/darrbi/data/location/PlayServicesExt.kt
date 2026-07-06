package com.mytm.darrbi.data.location

import com.google.android.gms.tasks.Task
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspend bridge for a Play-services [Task] without the coroutines-play-services artifact. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

internal fun <T> Result<T>.toApiResult(): ApiResult<T> = fold(
    onSuccess = { ApiResult.Success(it) },
    onFailure = { throwable ->
        // `runCatching {}` at the call sites catches coroutine cancellation too; rethrow it so cancellation
        // propagates normally instead of surfacing as a bogus "StandaloneCoroutine was cancelled" error
        // (e.g. each keystroke cancels the previous place-autocomplete search).
        if (throwable is CancellationException) throw throwable
        ApiResult.Failure(AppError.Unknown(throwable.message))
    },
)
