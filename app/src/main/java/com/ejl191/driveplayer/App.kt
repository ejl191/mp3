package com.ejl191.driveplayer

import android.app.Application
import android.content.Context
import com.ejl191.driveplayer.data.AppDatabase
import com.ejl191.driveplayer.data.Library
import com.ejl191.driveplayer.drive.DriveApi
import com.ejl191.driveplayer.drive.GoogleAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class App : Application() {
    /** For writes that must finish even if the screen or service that started them goes away. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val http = OkHttpClient()

    lateinit var db: AppDatabase
        private set
    lateinit var auth: GoogleAuth
        private set
    lateinit var drive: DriveApi
        private set
    lateinit var library: Library
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        auth = GoogleAuth(this)
        drive = DriveApi(auth, http)
        library = Library(this, db.dao(), drive)
    }
}

val Context.app: App get() = applicationContext as App
