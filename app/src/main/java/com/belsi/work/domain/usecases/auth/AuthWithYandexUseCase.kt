package com.belsi.work.domain.usecases.auth

import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.AuthResult
import javax.inject.Inject

class AuthWithYandexUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(yandexToken: String): Result<AuthResult> {
        return authRepository.authWithYandex(yandexToken)
    }
}
