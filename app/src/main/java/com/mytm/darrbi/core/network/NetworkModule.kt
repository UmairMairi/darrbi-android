package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.EnvConfig
import com.mytm.darrbi.data.remote.service.AuthApi
import com.mytm.darrbi.data.remote.service.OnboardingApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * One shared [OkHttpClient] + four qualified [Retrofit] instances (Main/Cms/Dashboard/Rental), all using
 * a kotlinx.serialization converter. Base URLs come from [EnvConfig] (per-flavor BuildConfig).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideConverterFactory(json: Json): Converter.Factory =
        json.asConverterFactory("application/json".toMediaType())

    @Provides
    @Singleton
    fun provideOkHttpClient(
        auth: AuthInterceptor,
        language: LanguageInterceptor,
        sessionExpiry: SessionExpiryInterceptor,
        envConfig: EnvConfig,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(language)
            .addInterceptor(sessionExpiry)
        if (envConfig.isDebug) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @MainApi
    fun provideMainRetrofit(client: OkHttpClient, factory: Converter.Factory, env: EnvConfig): Retrofit =
        buildRetrofit(env.mainUrl, client, factory)

    @Provides
    @Singleton
    @CmsApi
    fun provideCmsRetrofit(client: OkHttpClient, factory: Converter.Factory, env: EnvConfig): Retrofit =
        buildRetrofit(env.cmsUrl, client, factory)

    @Provides
    @Singleton
    @DashboardApi
    fun provideDashboardRetrofit(client: OkHttpClient, factory: Converter.Factory, env: EnvConfig): Retrofit =
        buildRetrofit(env.dashboardUrl, client, factory)

    @Provides
    @Singleton
    @RentalApi
    fun provideRentalRetrofit(client: OkHttpClient, factory: Converter.Factory, env: EnvConfig): Retrofit =
        buildRetrofit(env.rentalUrl, client, factory)

    @Provides
    @Singleton
    fun provideAuthApi(@MainApi retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOnboardingApi(@MainApi retrofit: Retrofit): OnboardingApi =
        retrofit.create(OnboardingApi::class.java)

    @Provides
    @Singleton
    fun provideRideApi(@MainApi retrofit: Retrofit): com.mytm.darrbi.data.remote.service.RideApi =
        retrofit.create(com.mytm.darrbi.data.remote.service.RideApi::class.java)

    @Provides
    @Singleton
    fun provideCmsApi(@CmsApi retrofit: Retrofit): com.mytm.darrbi.data.remote.service.CmsApi =
        retrofit.create(com.mytm.darrbi.data.remote.service.CmsApi::class.java)

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, factory: Converter.Factory): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(factory)
            .build()

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
