package com.example.wabackup

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
import okhttp3.*
import java.io.File
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.drive.Drive
import kotlinx.coroutines.*

class BackupService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var workManager: WorkManager
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BackupServiceChannel"
        private const val WORK_NAME = "BackupWork"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        workManager = WorkManager.getInstance(applicationContext)
        startForeground(NOTIFICATION_ID, createNotification("WhatsApp Backup Service Running"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupBackupWorker()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Backup Service"
            val descriptionText = "WhatsApp Backup Service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("WhatsApp Backup")
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_menu_upload)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun setupBackupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            15, TimeUnit.MINUTES,  // Minimum interval allowed
            5, TimeUnit.MINUTES    // Flex interval
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            backupWorkRequest
        )
    }

    inner class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val prefs = applicationContext.getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
            val backupType = prefs.getString(MainActivity.PREF_BACKUP_TYPE, "bytescale")
            val phoneNumber = prefs.getString(MainActivity.PREF_PHONE, "") ?: return Result.failure()
            val path = prefs.getString("backup_path", "") ?: return Result.failure()

            return try {
                when (backupType) {
                    "bytescale" -> uploadToBytescale(path, phoneNumber)
                    "drive" -> uploadToGoogleDrive(path, phoneNumber)
                    else -> Result.failure()
                }
            } catch (e: Exception) {
                Result.failure()
            }
        }

        private suspend fun uploadToBytescale(path: String, phoneNumber: String): Result {
            val apiKey = applicationContext.getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
                .getString(MainActivity.PREF_API_KEY, "") ?: return Result.failure()

            return withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (!file.exists()) return@withContext Result.failure()

                    val client = OkHttpClient()

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "file",
                            "${phoneNumber}_${file.name}",
                            RequestBody.create(MediaType.parse("application/octet-stream"), file)
                        )
                        .build()

                    val request = Request.Builder()
                        .url("https://api.bytescale.com/v2/accounts/G22nhY8/uploads/binary")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) Result.success()
                        else Result.retry()
                    }
                } catch (e: Exception) {
                    Result.retry()
                }
            }
        }

        private suspend fun uploadToGoogleDrive(path: String, phoneNumber: String): Result {
            // Google Drive upload implementation
            // This will be implemented when we add Google Drive authentication
            return Result.success()
        }
    }
}