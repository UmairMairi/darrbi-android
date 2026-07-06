package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.LanguageProvider
import com.mytm.darrbi.core.common.SessionEvent
import com.mytm.darrbi.core.common.SessionEventBus
import com.mytm.darrbi.core.common.SessionProvider
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject

/** Adds `Content-Type` and the current `sessionId` (read synchronously from the cached provider). */
class AuthInterceptor @Inject constructor(
    private val sessionProvider: SessionProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Content-Type", "application/json")
        sessionProvider.sessionId
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.header("sessionId", it) }
        return chain.proceed(builder.build())
    }
}

/** Adds the `Accept-Language` header from the current language ("en"/"ar"). */
class LanguageInterceptor @Inject constructor(
    private val languageProvider: LanguageProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept-Language", languageProvider.language)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Emits [SessionEvent.SessionExpired] when a response signals an expired token, so the shell can present
 * the session-expired sheet and force a logout.
 *
 * The backend signals expiry either at the HTTP layer (401, or the custom 440 "Login timeout") or inside
 * the house envelope on an otherwise-2xx response: `{"statusCode":440,"message":"Token expired!"}`. We
 * check both. The body is peeked (never consumed) with a small byte cap, and only parsed when the "440"
 * marker is present, so the common success path stays allocation-free.
 */
class SessionExpiryInterceptor @Inject constructor(
    private val sessionEventBus: SessionEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (isSessionExpired(response)) {
            sessionEventBus.tryEmit(SessionEvent.SessionExpired)
        }
        return response
    }

    private fun isSessionExpired(response: Response): Boolean {
        // Transport-level: 401 Unauthorized or the backend's custom 440 token-expiry code.
        if (response.code == 401 || response.code == TOKEN_EXPIRED_CODE) return true
        // Body-level: the house envelope can carry the expiry code while the HTTP status is 2xx.
        val body = runCatching { response.peekBody(PEEK_LIMIT_BYTES).string() }.getOrNull()
        if (body.isNullOrBlank() || !body.contains(TOKEN_EXPIRED_CODE.toString())) return false
        // Require a parseable envelope with a top-level statusCode of exactly 440 (no false positives from
        // a large truncated body — that fails to parse and is treated as not-expired).
        return runCatching { JSONObject(body).optInt("statusCode") == TOKEN_EXPIRED_CODE }.getOrDefault(false)
    }

    private companion object {
        /** Backend's non-standard "token expired / login timeout" status code. */
        const val TOKEN_EXPIRED_CODE = 440
        const val PEEK_LIMIT_BYTES = 4_096L
    }
}
