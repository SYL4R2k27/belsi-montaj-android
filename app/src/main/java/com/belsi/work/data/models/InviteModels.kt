package com.belsi.work.data.models

import java.util.UUID

enum class InviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED;
    
    val displayName: String
        get() = when (this) {
            PENDING -> "Ожидает ответа"
            ACCEPTED -> "Принят"
            REJECTED -> "Отклонен"
            EXPIRED -> "Истек"
        }
}

data class Invite(
    val id: UUID = UUID.randomUUID(),
    val code: String,
    val foremanId: UUID,
    val foremanName: String,
    val foremanPhone: String,
    val installerId: UUID? = null,
    val status: InviteStatus = InviteStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val acceptedAt: Long? = null,
    val note: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt && status == InviteStatus.PENDING
    
    val isActive: Boolean
        get() = status == InviteStatus.PENDING && !isExpired
}

data class Team(
    val id: UUID = UUID.randomUUID(),
    val foremanId: UUID,
    val foremanName: String,
    val members: List<TeamMember> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class TeamMember(
    val id: UUID,
    val name: String,
    val phone: String,
    val joinedAt: Long,
    val isActive: Boolean = true,
    val totalShifts: Int = 0,
    val totalHours: Int = 0
)
