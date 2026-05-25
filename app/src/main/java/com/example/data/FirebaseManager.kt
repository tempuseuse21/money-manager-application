package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var isInitialized = false

    fun init(context: Context): Boolean {
        if (isInitialized) return true
        try {
            // Check if already initialized by system
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                isInitialized = true
                Log.d(TAG, "Firebase already initialized by platform.")
                return true
            }

            val apiKey = try { BuildConfig.FIREBASE_API_KEY } catch (e: Throwable) { "" }
            val appId = try { BuildConfig.FIREBASE_APP_ID } catch (e: Throwable) { "" }
            val projectId = try { BuildConfig.FIREBASE_PROJECT_ID } catch (e: Throwable) { "" }

            // Validate that we have actual credentials rather than empty strings or default placeholders
            if (apiKey.isBlank() || apiKey == "YOUR_FIREBASE_API_KEY" || apiKey == "YOUR_API_KEY_HERE" || apiKey == "MY_GEMINI_API_KEY") {
                Log.w(TAG, "Valid Firebase API Key not found. Programmatic init skipped.")
                return false
            }

            val builder = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)

            val storageBucket = try { BuildConfig.FIREBASE_STORAGE_BUCKET } catch (e: Throwable) { "" }
            if (storageBucket.isNotBlank() && storageBucket != "YOUR_FIREBASE_STORAGE_BUCKET") {
                builder.setStorageBucket(storageBucket)
            }

            val messagingSenderId = try { BuildConfig.FIREBASE_MESSAGING_SENDER_ID } catch (e: Throwable) { "" }
            if (messagingSenderId.isNotBlank() && messagingSenderId != "YOUR_FIREBASE_MESSAGING_SENDER_ID") {
                builder.setGcmSenderId(messagingSenderId)
            }

            FirebaseApp.initializeApp(context.applicationContext, builder.build())
            isInitialized = true
            Log.d(TAG, "Firebase programmatically initialized successfully with user credentials.")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Error programmatically initializing Firebase: ${e.message}", e)
            isInitialized = false
            return false
        }
    }

    val isFirebaseAvailable: Boolean
        get() = isInitialized

    val firestore: FirebaseFirestore?
        get() = if (isInitialized) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Throwable) {
                Log.e(TAG, "Error getting Firestore instance: ${e.message}", e)
                null
            }
        } else null
}
