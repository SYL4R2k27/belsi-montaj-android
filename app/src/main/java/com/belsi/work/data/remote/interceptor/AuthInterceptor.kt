package com.belsi.work.data.remote.interceptor

import com.belsi.work.data.local.PrefsManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor для автоматической обработки 401 ошибок
 *
 * При получении 401 (Unauthorized):
 * 1. Очищает токен из хранилища
 * 2. Возвращает 401 ответ
 * 3. UI должен перенаправить пользователя на экран авторизации
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val prefsManager: PrefsManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // FIX(2026-05-14) BELSI 2.0.1: UI test mode header.
        // При активном TestMode добавляем `X-Belsi-Test: 1` к каждому запросу.
        // Backend по этому header'у:
        //  - На /auth/phone — НЕ дёргает sms.ru, сохраняет код = последние 4 цифры
        //  - На прочих endpoint'ах — работает как обычно (но логирует test-trace)
        // См. docs/ui-testing.md.
        if (com.belsi.work.testmode.TestMode.isTestRun) {
            request = request.newBuilder()
                .header("X-Belsi-Test", "1")
                .build()
        }

        val response = chain.proceed(request)

        // Обработка 401 Unauthorized
        if (response.code == 401) {
            // Токен истёк или невалидный - очищаем хранилище
            prefsManager.clearToken()
            prefsManager.clearAuthExtras()

            // Возвращаем 401 response, чтобы UI мог показать экран авторизации
            return response
        }

        return response
    }
}
