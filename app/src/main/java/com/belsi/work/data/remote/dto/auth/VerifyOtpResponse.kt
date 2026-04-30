package com.belsi.work.data.remote.dto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyOtpResponse(
    val token: String,
    val phone: String,
    @SerialName("is_new") val isNew: Boolean = false,
    val role: String = "installer"
)
