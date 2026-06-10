package com.mytm.darrbi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.common.SessionProvider
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.UserProfile
import com.mytm.darrbi.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the session. Persists to DataStore and keeps a `@Volatile` cache in sync
 * (collected on an app scope) so the auth interceptor can read [sessionId] synchronously — no `runBlocking`.
 */
@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) : SessionProvider, UserIdProvider, SessionRepository {

    @Volatile
    override var sessionId: String? = null
        private set

    @Volatile
    override var userId: String? = null
        private set

    @Volatile
    override var captain: CaptainDetails? = null
        private set

    @Volatile
    override var user: UserProfile? = null
        private set

    override fun saveCaptain(details: CaptainDetails) {
        captain = details
    }

    override fun saveUser(profile: UserProfile) {
        user = profile
    }

    private val sessionIdFlow: Flow<String?> = dataStore.data.map { it[Keys.SESSION_ID] }
    override val isLoggedIn: Flow<Boolean> = sessionIdFlow.map { !it.isNullOrBlank() }

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                sessionId = prefs[Keys.SESSION_ID]
                userId = prefs[Keys.USER_ID]
            }
        }
    }

    override suspend fun saveSession(token: String, userId: String?) {
        // Replace the in-memory token immediately so the very next request (e.g. right after verify-OTP)
        // uses the fresh token — the DataStore collector also refreshes it, but only asynchronously.
        sessionId = token
        if (userId != null) this.userId = userId
        dataStore.edit { prefs ->
            prefs[Keys.SESSION_ID] = token
            if (userId != null) prefs[Keys.USER_ID] = userId
        }
    }

    override suspend fun clear() {
        sessionId = null
        userId = null
        captain = null
        user = null
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val SESSION_ID = stringPreferencesKey("session_id")
        val USER_ID = stringPreferencesKey("user_id")
    }
}
