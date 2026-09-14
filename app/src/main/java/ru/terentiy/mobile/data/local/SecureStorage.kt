package ru.terentiy.mobile.data.local

/**
 * Хранилище значений, которые не должны лежать на диске открытым текстом:
 * идентификатор устройства и mobile bearer token.
 */
interface SecureStorage {

    /**
     * Идентификатор этого устройства для backend.
     *
     * Создаётся при первом обращении и дальше не меняется. Это случайный UUID,
     * а не ANDROID_ID: идентификатор устройства — персональные данные, и отправлять
     * его ради технического ключа незачем.
     * См. docs/adr/0004-proiskhozhdenie-device-id.md.
     */
    fun deviceId(): String

    fun token(): String?

    fun saveToken(value: String)

    fun clearToken()
}
