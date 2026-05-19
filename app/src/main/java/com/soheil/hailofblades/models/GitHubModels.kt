package com.soheil.hailofblades.models

import com.google.gson.annotations.SerializedName

data class WorkflowDispatchRequest(
    @SerializedName("ref") val ref: String,
    @SerializedName("inputs") val inputs: Map<String, String>
)

data class GitHubContent(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("content") val content: String,  // base64 encoded
    @SerializedName("encoding") val encoding: String
)
