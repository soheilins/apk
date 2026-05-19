package com.soheil.hailofblades

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.soheil.hailofblades.models.GitHubContentItem
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class FolderManagerActivity : AppCompatActivity() {

    private lateinit var token: String
    private lateinit var owner: String
    private lateinit var repo: String
    private lateinit var api: GitHubApi
    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_manager)

        token = intent.getStringExtra("token") ?: ""
        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        container = findViewById(R.id.foldersContainer)
        progressBar = findViewById(R.id.progressBarFolders)

        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("Authorization", "token $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build())
            }.build()

        api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)

        loadFolders()
    }

    private fun loadFolders() {
        progressBar.isVisible = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = api.getContents(owner, repo, "")
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!
                    val folders = items.filter { it.type == "dir" && it.name.matches(Regex("\\d{10}")) }
                    withContext(Dispatchers.Main) {
                        displayFolders(folders)
                        progressBar.isVisible = false
                    }
                } else {
                    showError("خطا در دریافت لیست پوشه‌ها: ${response.code()}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError(e.message ?: "خطا") }
            }
        }
    }

    private fun displayFolders(folders: List<GitHubContentItem>) {
        container.removeAllViews()
        if (folders.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "هیچ پوشه قبلی یافت نشد"
                setTextColor(getColor(R.color.textSecondary))
                gravity = android.view.Gravity.CENTER
            })
            return
        }
        for (folder in folders) {
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
                radius = 12f   // ✅ درست – گوشه‌های گرد
                setCardBackgroundColor(getColor(R.color.surface))
            }
            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
            }
            val nameView = TextView(this).apply {
                text = folder.name
                textSize = 16f
                setTextColor(getColor(R.color.textPrimary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val deleteButton = Button(this).apply {
                text = "حذف"
                setTextColor(getColor(R.color.accentRune))
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { confirmDelete(folder.name) }
            }
            innerLayout.addView(nameView)
            innerLayout.addView(deleteButton)
            card.addView(innerLayout)
            container.addView(card)
        }
    }

    private fun confirmDelete(folderName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("حذف پوشه")
            .setMessage("آیا پوشه «$folderName» و تمام محتویات آن حذف شود؟")
            .setPositiveButton("بله") { _, _ -> deleteFolder(folderName) }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun deleteFolder(folderName: String) {
        progressBar.isVisible = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val contentsResponse = api.getContents(owner, repo, folderName)
                if (!contentsResponse.isSuccessful) throw IOException("خطا در دریافت محتویات")
                val files = contentsResponse.body() ?: emptyList()
                for (file in files) {
                    val deleteResponse = api.deleteFile(owner, repo, file.path, "delete by HOB", file.sha, "main")
                    if (!deleteResponse.isSuccessful) throw IOException("خطا در حذف ${file.path}")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FolderManagerActivity, "پوشه $folderName با موفقیت حذف شد", Toast.LENGTH_LONG).show()
                    loadFolders()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError(e.message ?: "خطا در حذف") }
            } finally {
                withContext(Dispatchers.Main) { progressBar.isVisible = false }
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        progressBar.isVisible = false
    }
}
