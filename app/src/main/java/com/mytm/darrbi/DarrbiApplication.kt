package com.mytm.darrbi

import android.app.Application
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.domain.repository.SessionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DarrbiApplication : Application() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Start every launch with a clean slate: no stale session/userId in preferences. The session is
        // (re)populated only when a token is received from verify-OTP, and that token is used for all APIs.
        applicationScope.launch { sessionRepository.clear() }
    }
}
