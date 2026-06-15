package com.mytm.darrbi.data.location

import com.google.android.gms.tasks.Task
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import kotlinx.coroutines.suspendCancellableCoroutine
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
    onFailure = { ApiResult.Failure(AppError.Unknown(it.message)) },
)
