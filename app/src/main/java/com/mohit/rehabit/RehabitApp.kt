package com.mohit.rehabit

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.data.RehabitDatabase
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

    // Every future schema change needs a Migration here: never a destructive fallback.
    val db: RehabitDatabase = Room.databaseBuilder(context, RehabitDatabase::class.java, "rehabit.db").build()
}

val Context.container: AppContainer
    get() = (applicationContext as RehabitApp).container
