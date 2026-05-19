package com.soheil.hailofblades

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var ownerInput: EditText
    private lateinit var repoInput: EditText
    private lateinit var proxyInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var filenameInput: EditText
    private lateinit var startButton: Button
    private lateinit var saveSettingsButton: Button
    private lateinit var manageFoldersButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var logText: TextView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "hailofblades_prefs"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_OWNER = "repo_owner"
        private const val KEY_REPO = "repo_name"
        private const val KEY_PROXY = "proxy"
        private const val REQUEST_CODE_MANAGE_STORAGE = 1001
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
        saveSettingsButton = findViewById(R.id.saveSettingsButton)
        manageFoldersButton = findViewById(R.id.manageFoldersButton)
        progressBar = findViewById(R.id.progressBar)
        logText = findViewById(R.id.logText)
        val clearLogButton = findViewById<Button>(R.id.clearLogButton)
        val telegramLink = findViewById<TextView>(R.id.telegramLink)
        val githubLink = findViewById<TextView>(R.id.githubLink)

        logText.movementMethod = ScrollingMovementMethod()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedSettings()

        clearLogButton.setOnClickListener { logText.text = "" }

        telegramLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Hailofblades")))
        }
        githubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/soheilditf5-svg")))
        }

        saveSettingsButton.setOnClickListener { saveSettings() }

        startButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                log("لطفاً آدرس فایل را وارد کنید", true)
                return@setOnClickListener
            }
            if (!hasStoragePermission()) {
                requestStoragePermission()
                return@setOnClickListener
            }
            startDownload()
        }

        manageFoldersButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            val owner = ownerInput.text.toString().trim()
            val repo = repoInput.text.toString().trim()
            if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
                log("لطفاً توکن، مالک و نام ریپازیتوری را تنظیم کنید", true)
                return@setOnClickListener
            }
            val intent = Intent(this, FolderManagerActivity::class.java)
            intent.putExtra("token", token)
            intent.putExtra("owner", owner)
            intent.putExtra("repo", repo)
            startActivity(intent)
        }

        checkPermissions()
    }

    private fun loadSavedSettings() {
        tokenInput.setText(prefs.getString(KEY_TOKEN, ""))
        ownerInput.setText(prefs.getString(KEY_OWNER, ""))
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
        log("تنظیمات ذخیره شد", false)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            } catch (e: Exception) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_CODE_MANAGE_STORAGE)
            }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_MANAGE_STORAGE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                log("دسترسی به حافظه داده شد", false)
            else
                log("دسترسی به حافظه رد شد", true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (hasStoragePermission()) log("دسترسی کامل به حافظه داده شد", false)
            else log("دسترسی به حافظه رد شد", true)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun startDownload() {
        val token = tokenInput.text.toString().trim()
        val owner = ownerInput.text.toString().trim()
        val repo = repoInput.text.toString().trim()
        val proxy = proxyInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val fileUrl = urlInput.text.toString().trim()
        val customFilename = filenameInput.text.toString().trim()

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            log("لطفاً توکن، مالک و نام ریپازیتوری را وارد کنید", true)
            return
        }

        val data = androidx.work.Data.Builder()
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
                        log("✅ دانلود کامل شد! فایل در پوشه hob_downloaded ذخیره شد", false)
                        sendNotification()
                    }
                    WorkInfo.State.FAILED -> {
                        progressBar.visibility = View.GONE
                        val error = info.outputData.getString("error") ?: "خطای ناشناخته"
                        log("❌ شکست: $error", true)
                    }
                    else -> {}
                }
            }
    }

    private fun log(msg: String, isError: Boolean) {
        runOnUiThread {
            logText.append("${if (isError) "❌ " else "✓ "}$msg\n")
            (logText.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(android.app.NotificationChannel("download_channel", "Downloads", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        notificationManager.notify(1, androidx.core.app.NotificationCompat.Builder(this, "download_channel")
            .setContentTitle("HOB")
            .setContentText("دانلود فایل با موفقیت انجام شد!")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build())
    }
}
