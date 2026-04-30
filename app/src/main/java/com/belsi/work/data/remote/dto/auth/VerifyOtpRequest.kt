package com.belsi.work.data.remote.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class VerifyOtpRequest(
    val phone: String,
    val code: String
)
