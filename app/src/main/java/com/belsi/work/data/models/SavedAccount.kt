package com.belsi.work.data.models

/**
 * FIX(2026-05-22) BELSI 2.1.0: сохранённый профиль для мульти-аккаунта.
 * Храним до 5 штук (для корпоративных телефонов, переходящих между людьми).
 * Пароль/токен НЕ храним — переключение по паролю (выбор профиля → экран пароля
 * с предзаполненным логином).
 */
data class SavedAccount(
    val login: String,            // что вводит юзер: телефон / email / логин
    val name: String,             // отображаемое ФИО
    val phoneDisplay: String? = null,
    val role: String? = null,     // роль (lowercase) — для иконки/подписи
)
