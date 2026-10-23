package com.familytracker.app

import android.app.Application
import com.familytracker.app.auth.AuthRepository
import com.familytracker.app.circles.CircleRepository
import com.familytracker.app.data.*
import com.familytracker.app.settings.SettingsStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.*

class TrackerApp : Application() {
    var graph: AppGraph? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val app =
            if (BuildConfig.USE_EMULATORS)
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setProjectId("demo-family-tracker")
                        .setApplicationId("1:123456789:android:emulator")
                        .setApiKey("demo-api-key")
                        .setDatabaseUrl("https://demo-family-tracker-default-rtdb.firebaseio.com")
                        .build(),
                )
            else FirebaseApp.initializeApp(this)
        if (app != null && !app.options.databaseUrl.isNullOrBlank()) graph = AppGraph(this)
    }
}

class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val firebaseAuth = FirebaseAuth.getInstance()
    val db = FirebaseDatabase.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    init {
        // Our bounded SQLite outbox is the only durable location cache. Avoid cross-account disk
        // caches.
        db.setPersistenceEnabled(false)
        firestore.firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        if (BuildConfig.USE_EMULATORS) {
            firebaseAuth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
            db.useEmulator(BuildConfig.EMULATOR_HOST, 9000)
            firestore.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
    }

    val settings = SettingsStore(app)
    val auth = AuthRepository(firebaseAuth)
    val outbox = LocationOutbox(app)
    val circles = CircleRepository(db, firestore, app)
    val locations = LocationRepository(db, firestore, firebaseAuth, outbox, scope)
}
