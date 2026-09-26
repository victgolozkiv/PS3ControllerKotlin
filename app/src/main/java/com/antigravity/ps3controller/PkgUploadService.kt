package com.antigravity.ps3controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PkgUploadService : Service() {

    companion object {
        const val CHANNEL_ID = "pkg_upload_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_PS3_IP = "extra_ps3_ip"
    }

    inner class UploadBinder : Binder() {
        fun getService(): PkgUploadService = this@PkgUploadService
    }

    private val binder = UploadBinder()
    private var progressCallback: ((Int, String) -> Unit)? = null
    private var completionCallback: ((Boolean, String) -> Unit)? = null
    private var uploadJob: Job? = null

    fun setCallbacks(
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        progressCallback = onProgress
        completionCallback  = onComplete
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriString = intent?.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
        val ps3Ip = intent.getStringExtra(EXTRA_PS3_IP) ?: return START_NOT_STICKY
        val uri = Uri.parse(uriString)
        val fileName = getFileNameFromUri(uri) ?: "package.pkg"

        startForeground(NOTIFICATION_ID, buildNotification(fileName, 0))
        startUpload(uri, fileName, ps3Ip)

        return START_NOT_STICKY
    }

    private fun startUpload(uri: Uri, fileName: String, ps3Ip: String) {
        val ftpManager = FtpManager { ps3Ip }
        val fileSize = getFileSizeFromUri(uri)

        uploadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("No se pudo abrir el archivo")

                val remotePath = "/dev_hdd0/packages/$fileName"
                val result = ftpManager.uploadStream(inputStream, fileSize, remotePath) { progress ->
                    updateNotification(fileName, progress)
                    progressCallback?.invoke(progress, fileName)
                }

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        showCompletionNotification(fileName, true)
                        completionCallback?.invoke(true, fileName)
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                        showCompletionNotification(fileName, false, err)
                        completionCallback?.invoke(false, err)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showCompletionNotification(fileName, false, e.message ?: "Error")
                    completionCallback?.invoke(false, e.message ?: "Error")
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Subida de Archivos PKG",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Muestra el progreso de subida FTP de archivos PKG a PS3"
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(fileName: String, progress: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("📦 Subiendo PKG ($progress%)")
            .setContentText("$fileName • $progress% completado")
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(fileName, progress))
    }

    private fun showCompletionNotification(fileName: String, success: Boolean, error: String? = null) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (success) "✅ PKG subido exitosamente" else "❌ Error al subir PKG")
            .setContentText(if (success) "$fileName → /dev_hdd0/packages/" else "$fileName: $error")
            .setAutoCancel(true)
            .build()
        notificationManager?.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = -1L
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1) size = it.getLong(index)
                }
            }
        }
        return size
    }

    override fun onDestroy() {
        uploadJob?.cancel()
        super.onDestroy()
    }
}
