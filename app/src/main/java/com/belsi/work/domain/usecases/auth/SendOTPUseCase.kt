package com.belsi.work.domain.usecases.auth

import com.belsi.work.data.repositories.AuthRepository
import javax.inject.Inject

class SendOTPUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String): Result<Unit> {
        // Validate phone number
        if (!isValidPhone(phone)) {
            return Result.failure(Exception("Неверный формат номера телефона"))
        }

        // Normalize phone number
        val normalized = normalizePhone(phone)

        return authRepository.sendOtp(normalized)
    }
    
    private fun isValidPhone(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length == 11 && digits.startsWith("7")
    }
    
    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return "+$digits"
    }
}
