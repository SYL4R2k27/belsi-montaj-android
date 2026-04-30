package com.belsi.work.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

// UUID Serializer для kotlinx.serialization
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
enum class UserRole {
    @SerialName("installer")
    INSTALLER,     // Монтажник
    @SerialName("foreman")
    FOREMAN,       // Бригадир
    @SerialName("coordinator")
    COORDINATOR,   // Координатор объекта
    @SerialName("curator")
    CURATOR;       // Куратор

    val title: String
        get() = when (this) {
            INSTALLER -> "Монтажник"
            FOREMAN -> "Бригадир"
            COORDINATOR -> "Координатор"
            CURATOR -> "Куратор"
        }

    val description: String
        get() = when (this) {
            INSTALLER -> "Работает на объектах под руководством бригадира"
            FOREMAN -> "Руководит монтажниками, контролирует смены"
            COORDINATOR -> "Координирует работу на объекте, контролирует бригадиров"
            CURATOR -> "Контролирует работу бригадиров и монтажников"
        }
}

@Serializable
data class User(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val phone: String,
    val name: String = "",
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    var role: UserRole? = null,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("foreman_id")
    val foremanId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("curator_id")
    val curatorId: UUID? = null,
    val email: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val balance: Double? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("short_id")
    val shortId: String? = null
) {
    /**
     * Получить отображаемое имя
     */
    fun displayName(): String {
        if (!fullName.isNullOrBlank() && fullName != phone) return fullName
        val name = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return name.ifBlank { phone }
    }
}
