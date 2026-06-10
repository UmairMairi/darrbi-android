package com.mytm.darrbi.core.network

import javax.inject.Qualifier

/** The four base-URL groups from ride-android, each backed by its own qualified Retrofit. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainApi
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CmsApi
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DashboardApi
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RentalApi
