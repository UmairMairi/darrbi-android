package com.mytm.darrbi.core.common

import javax.inject.Qualifier

/** A long-lived application-level [kotlinx.coroutines.CoroutineScope] (SupervisorJob). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
