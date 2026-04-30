package com.belsi.work.data.remote.dto.auth

import kotlinx.serialization.Serializable

/**
 * Response от POST /auth/phone
 * {"status": "ok"}
 */
@Serializable
data class SendOtpResponse(
    val status: String
)
