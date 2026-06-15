package com.mytm.darrbi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.common.SessionProvider
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.UserProfile
import com.mytm.darrbi.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the session. Persists to DataStore (token, userId, userType, onboarding
 * flag, and the user profile) and keeps a `@Volatile` cache in sync so the auth interceptor can read
 * [sessionId] synchronously. The persisted session powers auto-login: the splash routes straight to the
 * dashboard when [loadSession] finds a saved token.
 */
@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
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

    @Volatile
    override var userType: Int? = null
        private set

    @Volatile
    override var isNameUpdated: Boolean = false
        private set

    private val sessionIdFlow: Flow<String?> = dataStore.data.map { it[Keys.SESSION_ID] }
    override val isLoggedIn: Flow<Boolean> = sessionIdFlow.map { !it.isNullOrBlank() }

    init {
        scope.launch {
            dataStore.data.collect { prefs -> applyPrefs(prefs) }
        }
    }

    /** Mirror persisted prefs into the in-memory cache. */
    private fun applyPrefs(prefs: Preferences) {
        sessionId = prefs[Keys.SESSION_ID]
        userId = prefs[Keys.USER_ID]
        userType = prefs[Keys.USER_TYPE]
        isNameUpdated = prefs[Keys.IS_NAME_UPDATED] ?: false
        user = prefs[Keys.USER_PROFILE]?.let {
            runCatching { json.decodeFromString(UserProfile.serializer(), it) }.getOrNull()
        }
    }

    override suspend fun loadSession(): Boolean {
        applyPrefs(dataStore.data.first())
        return !sessionId.isNullOrBlank()
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

    override suspend fun saveUser(profile: UserProfile) {
        user = profile
        dataStore.edit { it[Keys.USER_PROFILE] = json.encodeToString(UserProfile.serializer(), profile) }
    }

    override suspend fun saveLoginState(userType: Int?, isNameUpdated: Boolean) {
        this.userType = userType
        this.isNameUpdated = isNameUpdated
        dataStore.edit { prefs ->
            if (userType != null) prefs[Keys.USER_TYPE] = userType
            prefs[Keys.IS_NAME_UPDATED] = isNameUpdated
        }
    }

    override fun saveCaptain(details: CaptainDetails) {
        captain = details
    }

    override suspend fun clear() {
        sessionId = null
        userId = null
        captain = null
        user = null
        userType = null
        isNameUpdated = false
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val SESSION_ID = stringPreferencesKey("session_id")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_TYPE = intPreferencesKey("user_type")
        val IS_NAME_UPDATED = booleanPreferencesKey("is_name_updated")
        val USER_PROFILE = stringPreferencesKey("user_profile")
    }
}
