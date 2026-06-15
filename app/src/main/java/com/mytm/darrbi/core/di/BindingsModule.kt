package com.mytm.darrbi.core.di

import com.mytm.darrbi.core.common.LanguageProvider
import com.mytm.darrbi.core.common.SessionProvider
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.core.datastore.LanguageStore
import com.mytm.darrbi.core.datastore.SessionStore
import com.mytm.darrbi.data.location.MapServiceImpl
import com.mytm.darrbi.data.location.PlacesRepositoryImpl
import com.mytm.darrbi.data.socket.SocketServiceImpl
import com.mytm.darrbi.data.repository.AuthRepositoryImpl
import com.mytm.darrbi.data.repository.OnboardingRepositoryImpl
import com.mytm.darrbi.data.repository.RideRepositoryImpl
import com.mytm.darrbi.domain.repository.AuthRepository
import com.mytm.darrbi.domain.repository.MapService
import com.mytm.darrbi.domain.repository.OnboardingRepository
import com.mytm.darrbi.domain.repository.PlacesRepository
import com.mytm.darrbi.domain.repository.RideRepository
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.repository.SocketService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds data-layer implementations to their domain/common interfaces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    // SessionStore is the single source of truth — exposed under each interface it satisfies.
    @Binds
    @Singleton
    abstract fun bindSessionProvider(impl: SessionStore): SessionProvider

    @Binds
    @Singleton
    abstract fun bindUserIdProvider(impl: SessionStore): UserIdProvider

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionStore): SessionRepository

    @Binds
    @Singleton
    abstract fun bindLanguageProvider(impl: LanguageStore): LanguageProvider

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindPlacesRepository(impl: PlacesRepositoryImpl): PlacesRepository

    @Binds
    @Singleton
    abstract fun bindRideRepository(impl: RideRepositoryImpl): RideRepository

    @Binds
    @Singleton
    abstract fun bindMapService(impl: MapServiceImpl): MapService

    @Binds
    @Singleton
    abstract fun bindSocketService(impl: SocketServiceImpl): SocketService
}
