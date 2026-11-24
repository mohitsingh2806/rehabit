package com.mohit.rehabit

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.data.AgendaRepository
import com.mohit.rehabit.data.MIGRATION_1_2
import com.mohit.rehabit.data.RehabitDatabase
import com.mohit.rehabit.data.SettingsRepository
import com.mohit.rehabit.reminders.Notifier
import com.mohit.rehabit.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RehabitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.appScope.launch { container.scheduler.rescheduleAll() }
    }
}

/** Simple manual dependency container (no DI framework needed; easy to reach from receivers). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val content = ContentRepository(context.assets, appScope)

    // Every schema change needs a Migration here: never a destructive fallback.
    val db: RehabitDatabase = Room.databaseBuilder(context, RehabitDatabase::class.java, "rehabit.db")
        .addMigrations(MIGRATION_1_2)
        .build()

    val settings = SettingsRepository(context)
    val agenda = AgendaRepository(context, content, db, settings)
    val scheduler = ReminderScheduler(context, agenda)

    init {
        agenda.onChanged = { scheduler.rescheduleAll() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as RehabitApp).container
