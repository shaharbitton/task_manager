package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.FirestoreSync
import com.example.data.TaskRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class TaskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Manual Firebase initialization to avoid needing google-services.json
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY.ifEmpty { "placeholder_api_key" })
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID.ifEmpty { "1:1234567890:android:placeholder" })
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID.ifEmpty { "placeholder-project-id" })
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    private val firestoreSync by lazy {
        FirestoreSync(database.taskDao(), database.userDao(), database.rewardDao(), database.shoppingDao())
    }
    val repository by lazy { 
        TaskRepository(database.taskDao(), database.userDao(), database.rewardDao(), database.shoppingDao(), firestoreSync) 
    }

}
