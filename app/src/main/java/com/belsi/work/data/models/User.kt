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

/**
 * Домен — крупная группировка ролей для жёсткой сегрегации UI.
 * Brandbook: монтажник не видит производство, водитель не видит монтажа.
 * Куратор — единственное исключение (домен OBSERVER).
 */
@Serializable
enum class UserDomain {
    INSTALLATION,  // Монтажный домен — installer, foreman, coordinator
    LOGISTICS,     // Логистика — driver, logistician
    PRODUCTION,    // Производство — production_chief, senior_worker, worker, supplier, engineer
    OBSERVER;      // Наблюдатель — curator (видит всё)

    val title: String
        get() = when (this) {
            INSTALLATION -> "Монтаж"
            LOGISTICS -> "Логистика"
            PRODUCTION -> "Производство"
            OBSERVER -> "Куратор"
        }

    val emoji: String
        get() = when (this) {
            INSTALLATION -> "🔨"
            LOGISTICS -> "🚛"
            PRODUCTION -> "🏭"
            OBSERVER -> "👑"
        }
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
    CURATOR,       // Куратор
    // FIX(2026-05-03): роли для интеграции BELSI.Driver
    @SerialName("driver")
    DRIVER,        // Водитель — выполняет маршруты доставки
    @SerialName("logistician")
    LOGISTICIAN,   // Логист — формирует маршруты
    // FIX(2026-05-05): роли производства мебели (BELSI.Команда)
    @SerialName("production_chief")
    PRODUCTION_CHIEF,  // Начальник производства — главный на фабрике
    @SerialName("senior_worker")
    SENIOR_WORKER,     // Старший работник — старший в группе
    @SerialName("worker")
    WORKER,            // Работник — базовая роль на фабрике
    @SerialName("supplier")
    SUPPLIER,          // Комплектатор — выдача материалов и инструмента
    @SerialName("engineer")
    ENGINEER;          // Инженер — чертежи и тех.задачи

    val title: String
        get() = when (this) {
            INSTALLER -> "Монтажник"
            FOREMAN -> "Бригадир"
            COORDINATOR -> "Координатор"
            CURATOR -> "Куратор"
            DRIVER -> "Водитель"
            LOGISTICIAN -> "Логист"
            PRODUCTION_CHIEF -> "Начальник производства"
            SENIOR_WORKER -> "Старший работник"
            WORKER -> "Работник"
            SUPPLIER -> "Комплектатор"
            ENGINEER -> "Инженер"
        }

    val description: String
        get() = when (this) {
            INSTALLER -> "Работает на объектах под руководством бригадира"
            FOREMAN -> "Руководит монтажниками, контролирует смены"
            COORDINATOR -> "Координирует работу на объекте, контролирует бригадиров"
            CURATOR -> "Контролирует работу бригадиров и монтажников"
            DRIVER -> "Доставляет материалы по маршруту от логиста"
            LOGISTICIAN -> "Формирует маршруты, назначает водителей"
            PRODUCTION_CHIEF -> "Главный на фабрике. Ставит задачи, формирует партии, видит фото всех"
            SENIOR_WORKER -> "Старший в группе работников. Аналог бригадира на стройке"
            WORKER -> "Базовая роль на фабрике. Смена, фото, задачи"
            SUPPLIER -> "Выдаёт материалы и инструмент работникам со склада"
            ENGINEER -> "Готовит чертежи и тех.задачи для всех ролей производства"
        }

    /** Эмоджи-значок для UI переключателя ролей */
    val emoji: String
        get() = when (this) {
            INSTALLER -> "🔨"
            FOREMAN -> "🛠"
            COORDINATOR -> "📋"
            CURATOR -> "👑"
            DRIVER -> "💼"
            LOGISTICIAN -> "🚛"
            PRODUCTION_CHIEF -> "🏭"
            SENIOR_WORKER -> "👥"
            WORKER -> "👷"
            SUPPLIER -> "📦"
            ENGINEER -> "📐"
        }

    /** Домен роли — для жёсткой сегрегации UI (Brandbook). */
    val domain: UserDomain
        get() = when (this) {
            INSTALLER, FOREMAN, COORDINATOR -> UserDomain.INSTALLATION
            DRIVER, LOGISTICIAN -> UserDomain.LOGISTICS
            PRODUCTION_CHIEF, SENIOR_WORKER, WORKER, SUPPLIER, ENGINEER -> UserDomain.PRODUCTION
            CURATOR -> UserDomain.OBSERVER
        }
}

/**
 * FIX(2026-05-05): мульти-роль до 3 capabilities (Brandbook · экосистема).
 * Решение принято: один аккаунт может иметь до 3 ролей из РАЗНЫХ доменов.
 *
 * Жёсткие правила:
 * 1. Максимум 3 роли на аккаунт (см. MAX_ROLES_PER_USER).
 * 2. В одном домене — обычно одна роль (нельзя быть и Работником, и Старшим).
 *    Исключение — комбинации внутри монтажа (бригадир-практик).
 * 3. Между доменами — любые комбинации, до 3.
 *
 * Примеры:
 * - Иван: Работник + Водитель + Монтажник = OK (3 разных домена)
 * - Пётр: Бригадир + Монтажник = OK (внутри монтажа, исключение)
 * - Оля:  Работник + Старший работник = НЕТ (один домен, разные уровни)
 */
object UserCapabilities {
    /** Жёсткий лимит ролей на одного пользователя. */
    const val MAX_ROLES_PER_USER = 3

    /** Комбинации внутри одного домена которые разрешены. */
    private val ALLOWED_INTRA_DOMAIN_PAIRS: Set<Set<UserRole>> = setOf(
        setOf(UserRole.FOREMAN, UserRole.INSTALLER),       // Бригадир-практик (монтаж)
        setOf(UserRole.SENIOR_WORKER, UserRole.WORKER),    // Старший-практик (производство)
    )

    /**
     * Проверка набора ролей.
     * @return null если ОК, или текст ошибки.
     */
    fun validate(roles: Set<UserRole>): String? {
        if (roles.isEmpty()) return "Нужна хотя бы одна роль"
        if (roles.size > MAX_ROLES_PER_USER) {
            return "Максимум $MAX_ROLES_PER_USER роли на одного пользователя"
        }
        // Группируем по доменам
        val byDomain = roles.groupBy { it.domain }
        for ((domain, rolesInDomain) in byDomain) {
            if (rolesInDomain.size <= 1) continue
            // В одном домене несколько ролей — проверяем allowed pairs
            val pair = rolesInDomain.toSet()
            if (pair !in ALLOWED_INTRA_DOMAIN_PAIRS) {
                return "Роли ${rolesInDomain.joinToString(" + ") { it.title }} нельзя совмещать в одном домене"
            }
        }
        return null
    }

    /** Быстрая проверка — может ли пользователь иметь этот набор ролей. */
    fun isAllowed(roles: Set<UserRole>): Boolean = validate(roles) == null

    /** Совместимость со старым API — пара ролей разрешена? */
    @Deprecated("Используй validate(setOf(a,b))", ReplaceWith("isAllowed(setOf(a, b))"))
    fun isPairAllowed(a: UserRole, b: UserRole): Boolean =
        a == b || isAllowed(setOf(a, b))
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
    val shortId: String? = null,
    // FIX(2026-05-01): sticky binding пользователь<->объект.
    // Переживает minimize/kill приложения. NULL = не привязан.
    @Serializable(with = UUIDSerializer::class)
    @SerialName("current_site_object_id")
    val currentSiteObjectId: UUID? = null,
    // FIX(2026-05-03): capabilities-массив для дуальных ролей (Driver+Installer и др.)
    // Если массив пуст или сервер ещё не отправляет поле — fallback на role.
    val capabilities: List<UserRole> = emptyList()
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

    /**
     * FIX(2026-05-03): Эффективный список ролей пользователя.
     * Если сервер прислал capabilities — используем их.
     * Если пусто — fallback на single-role (старая логика, обратная совместимость).
     */
    fun effectiveCapabilities(): List<UserRole> {
        if (capabilities.isNotEmpty()) return capabilities
        return listOfNotNull(role)
    }

    /** Есть ли у пользователя несколько ролей — переключатель в UI? */
    fun isDualRole(): Boolean = effectiveCapabilities().size > 1

    /** Может ли пользователь действовать в данной роли? */
    fun canActAs(targetRole: UserRole): Boolean =
        targetRole in effectiveCapabilities()
}

/**
 * FIX(2026-05-03): тестовые capabilities для debug-режима.
 * Используется в feature/driver-integration пока сервер не отдаёт capabilities.
 * После серверной миграции — этот хелпер удаляем.
 */
object TestCapabilities {
    /** Маппинг тестовых телефонов → наборы capabilities для Этапа A (mock-режим). */
    private val TEST_USERS: Map<String, List<UserRole>> = mapOf(
        // Тирских Владимир — installer + driver (универсал)
        "+79166859658" to listOf(UserRole.INSTALLER, UserRole.DRIVER),
        // Тирских (виртуальный) — curator + logistician (главный + диспетчер)
        "+79999919886" to listOf(UserRole.CURATOR, UserRole.LOGISTICIAN),
    )

    fun forPhone(phone: String?): List<UserRole>? = phone?.let { TEST_USERS[it] }
}
