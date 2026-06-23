package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Модель инструмента
 */
@Serializable
data class Tool(
    val id: String,
    val name: String,
    val category: String? = null,
    @SerialName("serial_number")
    val serialNumber: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("foreman_id")
    val foremanId: String? = null,
    val status: ToolStatus = ToolStatus.AVAILABLE,
    @SerialName("created_at")
    val createdAt: String
)

/**
 * Статус инструмента
 */
@Serializable
enum class ToolStatus {
    @SerialName("available")
    AVAILABLE,      // Доступен на складе

    @SerialName("issued")
    ISSUED,         // Выдан монтажнику

    @SerialName("lost")
    LOST,           // Потерян

    @SerialName("repair")
    REPAIR          // В ремонте
}

/**
 * Транзакция выдачи/возврата инструмента
 */
@Serializable
data class ToolTransaction(
    val id: String,
    @SerialName("tool_id")
    val toolId: String,
    @SerialName("tool_name")
    val toolName: String? = null,
    @SerialName("installer_id")
    val installerId: String,
    @SerialName("installer_name")
    val installerName: String? = null,
    @SerialName("issued_by")
    val issuedBy: String,
    @SerialName("issued_by_name")
    val issuedByName: String? = null,
    @SerialName("issued_at")
    val issuedAt: String,
    @SerialName("issue_photo_url")
    val issuePhotoUrl: String? = null,
    @SerialName("issue_comment")
    val issueComment: String? = null,

    @SerialName("returned_at")
    val returnedAt: String? = null,
    @SerialName("returned_to")
    val returnedTo: String? = null,
    @SerialName("returned_to_name")
    val returnedToName: String? = null,
    @SerialName("return_photo_url")
    val returnPhotoUrl: String? = null,
    @SerialName("return_condition")
    val returnCondition: ToolCondition? = null,
    @SerialName("return_comment")
    val returnComment: String? = null,

    val status: TransactionStatus,
    @SerialName("created_at")
    val createdAt: String
) {
    /**
     * Инструмент выдан (не возвращен)
     */
    val isActive: Boolean
        get() = status == TransactionStatus.ISSUED

    /**
     * Инструмент возвращен
     */
    val isReturned: Boolean
        get() = status == TransactionStatus.RETURNED
}

/**
 * Статус транзакции
 */
@Serializable
enum class TransactionStatus {
    @SerialName("issued")
    ISSUED,     // Выдано

    @SerialName("returned")
    RETURNED,   // Возвращено

    @SerialName("pending")
    PENDING     // Ожидает подтверждения
}

/**
 * Состояние инструмента при возврате
 */
@Serializable
enum class ToolCondition {
    @SerialName("good")
    GOOD,       // Хорошее

    @SerialName("damaged")
    DAMAGED,    // Поврежден

    @SerialName("broken")
    BROKEN      // Сломан
}

/**
 * Запрос на выдачу инструмента
 */
@Serializable
data class IssueToolRequest(
    @SerialName("tool_id")
    val toolId: String,
    @SerialName("installer_id")
    val installerId: String,
    val comment: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null
)

/**
 * Запрос на возврат инструмента
 */
@Serializable
data class ReturnToolRequest(
    @SerialName("return_condition")
    val returnCondition: ToolCondition,
    @SerialName("return_comment")
    val returnComment: String? = null,
    @SerialName("return_photo_url")
    val returnPhotoUrl: String? = null
)

/**
 * Запрос на создание нового инструмента
 */
@Serializable
data class CreateToolRequest(
    val name: String,
    val category: String? = null,
    @SerialName("serial_number")
    val serialNumber: String? = null
)

/**
 * Категория инструмента для справочника
 */
data class ToolCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String? = null
) {
    companion object {
        fun getDefaultCategories(): List<ToolCategory> = listOf(
            ToolCategory(name = "Электроинструмент", icon = "⚡"),
            ToolCategory(name = "Ручной инструмент", icon = "🔧"),
            ToolCategory(name = "Измерительный инструмент", icon = "📏"),
            ToolCategory(name = "Расходные материалы", icon = "📦"),
            ToolCategory(name = "Другое", icon = "🛠️")
        )
    }
}
