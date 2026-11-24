package com.havenheights.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.MutableSharedFlow
import com.havenheights.app.data.ApiFactory
import com.havenheights.app.data.Repository
import com.havenheights.app.data.SessionStore
import com.havenheights.app.data.SyncScheduler
import com.havenheights.app.data.local.LocalCache
import com.havenheights.app.data.local.LocalDao
import com.havenheights.app.data.local.LocalDb
import com.havenheights.app.push.PushRegistrar

class HavenApplication : Application() {
    lateinit var session: SessionStore; private set
    lateinit var repository: Repository; private set
    lateinit var dao: LocalDao; private set
    lateinit var cache: LocalCache; private set
    lateinit var push: PushRegistrar; private set

    /** Fired when a push arrives, so open screens refresh and show the change at once. */
    val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
        repository = Repository(ApiFactory.create(session))
        dao = Room.databaseBuilder(this, LocalDb::class.java, "haven_local.db").build().dao()
        cache = LocalCache(dao)
        push = PushRegistrar(this)
        scheduleSync()   // flush anything queued before the app was closed
    }

    fun scheduleSync() = SyncScheduler.enqueue(this)
}
