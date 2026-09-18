package com.example.saf

import android.net.Uri

data class LocalItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String? = null
)
