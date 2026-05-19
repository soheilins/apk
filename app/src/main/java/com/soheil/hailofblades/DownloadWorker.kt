package com.soheil.hailofblades

import android.content.Context
import android.os.Environment
import androidx.work.*
import com.soheil.hailofblades.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val token = inputData.getString("token") ?: return@withContext Result.failure()
            val owner = inputData.getString("owner") ?: return@withContext Result.failure()
            val repo = inputData.getString("repo") ?: return@withContext Result.failure()
            val proxyUrl = inputData.getString("proxy")
            val fileUrl = inputData.getString("fileUrl") ?: return@withContext Result.failure()
            val customFilename = inputData.getString("customFilename") ?: ""

            setProgress(Data.Builder().putInt("progress", 0).build())

            val okHttpBuilder = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "token $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    chain.proceed(request)
                }
            if (!proxyUrl.isNullOrEmpty()) {
                val proxyParts = proxyUrl.split(":")
                if (proxyParts.size >= 2) {
                    val host = proxyParts[0].replace("http://", "").replace("https://", "")
                    val port = proxyParts[1].toIntOrNull() ?: 8080
                    okHttpBuilder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                }
            }
            val client = okHttpBuilder.build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(GitHubApi::class.java)

            val folderName = java.text.SimpleDateFormat("MMddHHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            setProgress(Data.Builder().putInt("progress", 5).build())

            val dispatchBody = WorkflowDispatchRequest("main", mapOf("file_url" to fileUrl, "folder_name" to folderName))
            val dispatchResponse = api.triggerWorkflow(owner, repo, "download-split.yml", dispatchBody)
            if (!dispatchResponse.isSuccessful) {
                return@withContext Result.failure(workDataOf("error" to "Failed to trigger workflow: ${dispatchResponse.code()}"))
            }
            setProgress(Data.Builder().putInt("progress", 10).build())

            var originalFilename = "reassembled.bin"
            var completed = false
            repeat(180) { attempt ->
                if (!isActive) return@withContext Result.failure(workDataOf("error" to "Cancelled"))
                val content = api.getFileContent(owner, repo, "$folderName/_complete.txt")
                if (content.isSuccessful && content.body() != null) {
                    val decoded = String(android.util.Base64.decode(content.body()!!.content, android.util.Base64.DEFAULT))
                    decoded.split("\n").forEach { line ->
                        if (line.startsWith("filename=")) {
                            originalFilename = line.substringAfter("=").trim()
                        }
                    }
                    completed = true
                    return@repeat
                }
                delay(10000)
                val progress = 10 + (attempt * 0.5).toInt().coerceAtMost(70)
                setProgress(Data.Builder().putInt("progress", progress).build())
            }
            if (!completed) return@withContext Result.failure(workDataOf("error" to "Workflow timed out"))

            setProgress(Data.Builder().putInt("progress", 80).build())

            val zipResponse = api.downloadRepoZip(owner, repo)
            if (!zipResponse.isSuccessful) return@withContext Result.failure(workDataOf("error" to "Failed to download repo"))
            val tempZip = File(applicationContext.cacheDir, "repo.zip")
            zipResponse.body()?.byteStream()?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }

            val extractDir = File(applicationContext.cacheDir, "extracted")
            extractDir.mkdirs()
            ZipFile(tempZip).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val target = File(extractDir, entry.name)
                    if (entry.isDirectory) target.mkdirs()
                    else zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            val chunksDir = extractDir.walk().firstOrNull { it.isDirectory && it.name == folderName && File(it, "chunks").exists() }
                ?.let { File(it, "chunks") } ?: return@withContext Result.failure(workDataOf("error" to "Chunks folder not found"))

            val chunkFiles = chunksDir.listFiles { f -> f.name.matches(Regex("chunk_\\d+\\.part")) }
                ?.sortedBy { it.name.substringAfter("_").substringBefore(".").toInt() } ?: return@withContext Result.failure(workDataOf("error" to "No chunk files"))

            val finalName = if (customFilename.isNotBlank()) customFilename else originalFilename
            // This points to the primary shared storage (internal storage, not removable SD card)
            val outputDir = File(Environment.getExternalStorageDirectory(), "hob_downloaded")
            outputDir.mkdirs()
            val outputFile = File(outputDir, finalName)
            FileOutputStream(outputFile).use { fos ->
                chunkFiles.forEach { chunk ->
                    chunk.inputStream().use { input ->
                        input.copyTo(fos)
                    }
                }
            }

            try {
                api.triggerWorkflow(owner, repo, "delete-folder.yml", WorkflowDispatchRequest("main", mapOf("folder_name" to folderName)))
            } catch (e: Exception) { }

            tempZip.delete()
            extractDir.deleteRecursively()

            setProgress(Data.Builder().putInt("progress", 100).build())
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
}
