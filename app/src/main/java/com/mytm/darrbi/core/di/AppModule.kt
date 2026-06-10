package com.mytm.darrbi.core.di

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mytm.darrbi.BuildConfig
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.common.DefaultDispatchersProvider
import com.mytm.darrbi.core.common.DeviceInfoProvider
import com.mytm.darrbi.core.common.DispatchersProvider
import com.mytm.darrbi.core.common.EnvConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "darbi_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEnvConfig(): EnvConfig = EnvConfig(
        mainUrl = BuildConfig.MAIN_URL,
        cmsUrl = BuildConfig.CMS_URL,
        dashboardUrl = BuildConfig.DASHBOARD_URL,
        rentalUrl = BuildConfig.RENTAL_URL,
        rentalToken = BuildConfig.RENTAL_TOKEN,
        secretKey = BuildConfig.SECRET_KEY,
        isDebug = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun provideDispatchers(): DispatchersProvider = DefaultDispatchersProvider()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatchersProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideDeviceInfoProvider(@ApplicationContext context: Context): DeviceInfoProvider =
        object : DeviceInfoProvider {
            @SuppressLint("HardwareIds")
            override val deviceId: String =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }
}
