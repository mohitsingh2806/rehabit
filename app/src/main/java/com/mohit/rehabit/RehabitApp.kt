package com.mohit.rehabit

import android.app.Application
import android.content.Context
import com.mohit.rehabit.content.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RehabitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container (no DI framework needed; easy to reach from receivers). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val content = ContentRepository(context.assets, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as RehabitApp).container
