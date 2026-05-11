package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-10) BELSI 1.3.0: DTOs для AI endpoints.
 *
 * Соответствуют backend схемам в:
 * - curator.py (AiSummaryResponse, PhotoSearchResponse)
 * - factory_shifts.py (AiIdleVerifyResponse)
 * - voice_input.py (VoiceTranscribeResponse)
 * - ai_extras.py (SmartReplyResponse, TriageResponse, StockForecastResponse)
 *
 * Все вызовы идут через BELSI-backend, который проксирует в XeroCode.
 */

// ─────────────────────────────────────────────────────────────────
// daily_summary
// ─────────────────────────────────────────────────────────────────

@Serializable
data class AiDailySummaryResponse(
    val headline: String,
    val summary: String,
    val anomalies: List<AiAnomalyDto> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val cached: Boolean = false,
    @SerialName("cached_at") val cachedAt: String? = null,
)

@Serializable
data class AiAnomalyDto(
    val type: String,
    val user: String? = null,
    val `object`: String? = null,
    val value: String? = null,
)

// ─────────────────────────────────────────────────────────────────
// photo_search
// ─────────────────────────────────────────────────────────────────

@Serializable
data class AiPhotoSearchRequest(val query: String)

@Serializable
data class AiPhotoSearchResultItem(
    val id: String,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("site_object_name") val siteObjectName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("ai_score") val aiScore: Int? = null,
    @SerialName("ai_category") val aiCategory: String? = null,
    @SerialName("ai_comment") val aiComment: String? = null,
)

@Serializable
data class AiPhotoSearchResponse(
    @SerialName("intent_understood") val intentUnderstood: Boolean,
    @SerialName("explanation_ru") val explanationRu: String,
    @SerialName("filters_applied") val filtersApplied: Map<String, String> = emptyMap(),
    val photos: List<AiPhotoSearchResultItem>,
    @SerialName("total_found") val totalFound: Int,
)

// ─────────────────────────────────────────────────────────────────
// idle_verify
// ─────────────────────────────────────────────────────────────────

@Serializable
data class AiIdleVerifyResponse(
    val confirmed: Boolean,
    val confidence: Int,
    @SerialName("suspicion_score") val suspicionScore: Int,
    @SerialName("reason_visible") val reasonVisible: Boolean,
    val comment: String,
    @SerialName("photo_used_id") val photoUsedId: String? = null,
)

// ─────────────────────────────────────────────────────────────────
// voice_transcribe
// ─────────────────────────────────────────────────────────────────

@Serializable
data class VoiceTranscribeResponse(
    val text: String,
    @SerialName("language_detected") val languageDetected: String? = null,
    @SerialName("duration_sec") val durationSec: Double? = null,
    @SerialName("request_id") val requestId: String,
    @SerialName("model_used") val modelUsed: String? = null,
)

// ─────────────────────────────────────────────────────────────────
// smart_reply
// ─────────────────────────────────────────────────────────────────

@Serializable
data class SmartReplyRequest(
    @SerialName("thread_id") val threadId: String,
    @SerialName("incoming_message_id") val incomingMessageId: String? = null,
    @SerialName("incoming_text") val incomingText: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("chat_history") val chatHistory: List<Map<String, String>>? = null,
)

@Serializable
data class SmartReplyResponse(
    val replies: List<SmartReplyDto>,
    @SerialName("context_understood") val contextUnderstood: Boolean,
    @SerialName("request_id") val requestId: String,
)

@Serializable
data class SmartReplyDto(
    val text: String,
    val tone: String,  // agree | info | delay | reject
)

// ─────────────────────────────────────────────────────────────────
// triage_ticket
// ─────────────────────────────────────────────────────────────────

@Serializable
data class TriageTicketResponse(
    @SerialName("ticket_id") val ticketId: String,
    val category: String,
    val subcategory: String,
    val priority: String,  // low | normal | high | urgent
    val tags: List<String> = emptyList(),
    val sentiment: String,
    @SerialName("summary_short") val summaryShort: String,
    val blocking: Boolean = false,
    @SerialName("suggested_assignee_role") val suggestedAssigneeRole: String,
    @SerialName("request_id") val requestId: String,
)

// ─────────────────────────────────────────────────────────────────
// stock_forecast
// ─────────────────────────────────────────────────────────────────

@Serializable
data class StockForecastItem(
    @SerialName("material_code") val materialCode: String,
    @SerialName("material_name") val materialName: String,
    @SerialName("current_stock") val currentStock: Int,
    val unit: String,
    @SerialName("daily_avg_usage") val dailyAvgUsage: Double,
    @SerialName("days_left") val daysLeft: Int,
    val risk: String,  // critical | high | medium | low
    @SerialName("reason_ru") val reasonRu: String,
    @SerialName("recommended_order_quantity") val recommendedOrderQuantity: Int,
)

@Serializable
data class StockForecastResponse(
    val forecasts: List<StockForecastItem>,
    @SerialName("summary_ru") val summaryRu: String,
    val actions: List<String> = emptyList(),
    @SerialName("request_id") val requestId: String,
)
