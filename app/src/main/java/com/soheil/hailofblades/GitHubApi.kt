package com.soheil.hailofblades

import com.soheil.hailofblades.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GitHubApi {
    // قبلی
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun triggerWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body request: WorkflowDispatchRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Response<GitHubContent>

    @GET("repos/{owner}/{repo}/zipball/{ref}")
    @Streaming
    suspend fun downloadRepoZip(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String = "main"
    ): Response<ResponseBody>

    // جدید برای مدیریت پوشه‌ها
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Response<List<GitHubContentItem>>

    @DELETE("repos/{owner}/{repo}/contents/{path}")
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("message") message: String,
        @Query("sha") sha: String,
        @Query("branch") branch: String = "main"
    ): Response<Unit>
}
