package com.belsi.work.domain.usecases.shift

import com.belsi.work.data.repositories.ShiftData
import com.belsi.work.data.repositories.ShiftRepository
import javax.inject.Inject

/**
 * Use Case для начала смены через BELSI API
 *
 * POST /shifts/start
 */
class StartShiftUseCase @Inject constructor(
    private val shiftRepository: ShiftRepository
) {
    /**
     * Начать новую смену
     *
     * @return Result<ShiftData> с id смены, временем начала и статусом
     */
    suspend operator fun invoke(): Result<ShiftData> {
        return shiftRepository.startShift()
    }
}
