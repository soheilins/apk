package com.soheil.hailofblades

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var ownerInput: EditText
    private lateinit var repoInput: EditText
    private lateinit var proxyInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var filenameInput: EditText
    private lateinit var startButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var logText: TextView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "hailofblades_prefs"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_OWNER = "repo_owner"
        private const val KEY_REPO = "repo_name"
        private const val KEY_PROXY = "proxy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenInput = findViewById(R.id.tokenInput)
        ownerInput = findViewById(R.id.ownerInput)
        repoInput = findViewById(R.id.repoInput)
        proxyInput = findViewById(R.id.proxyInput)
        urlInput = findViewById(R.id.urlInput)
        filenameInput = findViewById(R.id.filenameInput)
        startButton = findViewById(R.id.startButton)
        progressBar = findViewById(R.id.progressBar)
        logText = findViewById(R.id.logText)
        val clearLogButton = findViewById<Button>(R.id.clearLogButton)

        logText.movementMethod = ScrollingMovementMethod()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedSettings()

        clearLogButton.setOnClickListener { logText.text = "" }

        startButton.setOnClickListener {
            saveSettings()
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                log("Please enter a file URL", true)
                return@setOnClickListener
            }
            if (!hasPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }
            startDownload()
        }

        checkPermissions()
    }

    private fun loadSavedSettings() {
        tokenInput.setText(prefs.getString(KEY_TOKEN, ""))
        ownerInput.setText(prefs.getString(KEY_OWNER, "soheilins"))
        repoInput.setText(prefs.getString(KEY_REPO, "directandyt"))
        proxyInput.setText(prefs.getString(KEY_PROXY, ""))
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putString(KEY_TOKEN, tokenInput.text.toString().trim())
            putString(KEY_OWNER, ownerInput.text.toString().trim())
            putString(KEY_REPO, repoInput.text.toString().trim())
            putString(KEY_PROXY, proxyInput.text.toString().trim())
            apply()
        }
        log("Settings saved", false)
    }

    private fun hasPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.POST_NOTIFICATIONS
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        log("All permissions granted", false)
                    } else {
                        log("Storage permissions denied – cannot save file", true)
                    }
                }
                override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>, token: PermissionToken) {
                    token.continuePermissionRequest()
                }
            }).check()
    }

    private fun checkPermissions() {
        if (!hasPermissions()) {
            requestPermissions()
        }
    }

    private fun startDownload() {
        val token = tokenInput.text.toString().trim()
        val owner = ownerInput.text.toString().trim()
        val repo = repoInput.text.toString().trim()
        val proxy = proxyInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val fileUrl = urlInput.text.toString().trim()
        val customFilename = filenameInput.text.toString().trim()

        val data = Data.Builder()
            .putString("token", token)
            .putString("owner", owner)
            .putString("repo", repo)
            .putString("proxy", proxy)
            .putString("fileUrl", fileUrl)
            .putString("customFilename", customFilename)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        // Observe progress
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { info ->
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt("progress", 0)
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = progress
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progressBar.visibility = View.GONE
                        log("✅ Download complete! File saved to hob_downloaded/", false)
                        sendNotification()
                    }
                    WorkInfo.State.FAILED -> {
                        progressBar.visibility = View.GONE
                        val error = info.outputData.getString("error") ?: "Unknown error"
                        log("❌ Failed: $error", true)
                    }
                    else -> {}
                }
            }
    }

    private fun log(msg: String, isError: Boolean) {
        val prefix = if (isError) "❌ " else "✓ "
        runOnUiThread {
            logText.append("$prefix$msg\n")
            val scrollView = logText.parent as? android.widget.ScrollView
            scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun sendNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                showNotification()
            }
        } else {
            showNotification()
        }
    }

    private fun showNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("download_channel", "Downloads", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, "download_channel")
            .setContentTitle("Hail of Blades")
            .setContentText("File download completed!")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()
        notificationManager.notify(1, notification)
    }
}
