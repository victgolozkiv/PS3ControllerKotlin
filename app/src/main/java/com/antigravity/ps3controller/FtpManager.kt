package com.antigravity.ps3controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class Ps3Item(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val fullPath: String
)

class FtpManager(private val hostProvider: () -> String) {

    suspend fun listFiles(remotePath: String): Result<List<Ps3Item>> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(hostProvider(), 21)
            client.login("anonymous", "")
            client.enterLocalPassiveMode()
            
            val files: Array<FTPFile> = client.listFiles(remotePath) ?: emptyArray()
            val list = files.map { file ->
                val name = file.name
                val cleanPath = if (remotePath.endsWith("/")) "$remotePath$name" else "$remotePath/$name"
                Ps3Item(
                    name = name,
                    isDirectory = file.isDirectory,
                    size = file.size,
                    fullPath = cleanPath
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            
            client.logout()
            client.disconnect()
            Result.success(list)
        } catch (e: Exception) {
            if (client.isConnected) {
                try { client.disconnect() } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connectTimeout = 3000
            client.connect(hostProvider(), 21)
            val success = client.login("anonymous", "")
            client.logout()
            client.disconnect()
            if (success) Result.success(true) else Result.failure(Exception("Credenciales rechazadas"))
        } catch (e: Exception) {
            if (client.isConnected) {
                try { client.disconnect() } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    suspend fun uploadStream(
        inputStream: java.io.InputStream,
        totalSize: Long,
        remotePath: String,
        onProgress: (Int) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connectTimeout = 5000
            client.connect(hostProvider(), 21)
            client.login("anonymous", "")
            client.enterLocalPassiveMode()
            client.setFileType(FTPClient.BINARY_FILE_TYPE)

            var bytesUploaded = 0L
            val outputStream = client.storeFileStream(remotePath)
                ?: throw Exception("No se pudo iniciar la transferencia en la ruta PS3: $remotePath")

            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesUploaded += bytesRead
                val progress = if (totalSize > 0) ((bytesUploaded * 100) / totalSize).toInt() else 0
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
            inputStream.close()
            outputStream.close()
            val completed = client.completePendingCommand()

            client.logout()
            client.disconnect()
            Result.success(completed)
        } catch (e: Exception) {
            try { inputStream.close() } catch (_: Exception) {}
            if (client.isConnected) {
                try { client.disconnect() } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    suspend fun uploadFile(localFile: File, remotePath: String, onProgress: (Int) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(hostProvider(), 21)
            client.login("anonymous", "")
            client.enterLocalPassiveMode()
            client.setFileType(FTPClient.BINARY_FILE_TYPE)

            val inputStream = FileInputStream(localFile)
            val totalSize = localFile.length()
            var bytesUploaded = 0L

            val outputStream = client.storeFileStream(remotePath)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesUploaded += bytesRead
                val progress = if (totalSize > 0) ((bytesUploaded * 100) / totalSize).toInt() else 0
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
            inputStream.close()
            outputStream.close()
            val completed = client.completePendingCommand()

            client.logout()
            client.disconnect()
            Result.success(completed)
        } catch (e: Exception) {
            if (client.isConnected) {
                try { client.disconnect() } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }
}

