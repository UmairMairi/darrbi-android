package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.LanguageProvider
import com.mytm.darrbi.core.common.SessionEvent
import com.mytm.darrbi.core.common.SessionEventBus
import com.mytm.darrbi.core.common.SessionProvider
import okhttp3.Interceptor
import okhttp3.Response
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

/** Emits [SessionEvent.SessionExpired] on any 401 so the shell can route to login. */
class SessionExpiryInterceptor @Inject constructor(
    private val sessionEventBus: SessionEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            sessionEventBus.tryEmit(SessionEvent.SessionExpired)
        }
        return response
    }
}
