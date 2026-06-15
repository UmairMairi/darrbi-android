package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Single source of truth for the authenticated session (replaces ride-android's `Singleton`/`PrefManager`). */
interface SessionRepository {
    val isLoggedIn: Flow<Boolean>

    /** Profile of the signed-in user (from verify-OTP); null before login. */
    val user: UserProfile?

    /** Captain details for the current session (set after login for a captain); null otherwise. */
    val captain: CaptainDetails?

    /** 1 = rider, 2 = captain; null if unknown. Persisted so the splash can route on relaunch. */
    val userType: Int?

    /** Whether the user has finished onboarding (selected type + set name). */
    val isNameUpdated: Boolean

    suspend fun saveSession(token: String, userId: String?)

    /** Persist (and cache) the verify-OTP user profile so it survives an app restart. */
    suspend fun saveUser(profile: UserProfile)

    /** Persist the user type + onboarding-complete flag used for auto-login routing. */
    suspend fun saveLoginState(userType: Int?, isNameUpdated: Boolean)

    /** Store the captain details fetched from `GET /captains` in the current session. */
    fun saveCaptain(details: CaptainDetails)

    /** Loads the persisted session into the in-memory cache; returns true if a token is saved. */
    suspend fun loadSession(): Boolean

    suspend fun clear()
}
