package com.belsi.work.data.remote.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class SendOtpRequest(
    val phone: String
)
