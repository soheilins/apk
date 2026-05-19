package com.soheil.hailofblades.models

import com.google.gson.annotations.SerializedName

data class WorkflowDispatchRequest(
    @SerializedName("ref") val ref: String,
    @SerializedName("inputs") val inputs: Map<String, String>
)

data class GitHubContent(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("content") val content: String,
    @SerializedName("encoding") val encoding: String
)

data class GitHubContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val type: String
)
