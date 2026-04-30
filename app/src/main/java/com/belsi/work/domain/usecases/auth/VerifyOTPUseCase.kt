package com.belsi.work.domain.usecases.auth

import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.AuthResult
import javax.inject.Inject

class VerifyOTPUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String, code: String): Result<AuthResult> {
        // Validate OTP code
        if (!isValidOTP(code)) {
            return Result.failure(Exception("Код должен состоять из 6 цифр"))
        }

        // Normalize phone
        val digits = phone.filter { it.isDigit() }
        val normalized = "+$digits"

        return authRepository.verifyOtp(normalized, code)
    }

    private fun isValidOTP(code: String): Boolean {
        return code.length == 6 && code.all { it.isDigit() }
    }
}
