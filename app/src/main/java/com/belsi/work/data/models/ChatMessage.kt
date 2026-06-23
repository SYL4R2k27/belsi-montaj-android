package com.belsi.work.data.models

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: Long,
    val isFromSupport: Boolean
)
