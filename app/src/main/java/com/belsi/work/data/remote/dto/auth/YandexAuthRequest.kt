package com.belsi.work.data.remote.dto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YandexAuthRequest(
    @SerialName("yandex_token")
    val yandexToken: String
)
