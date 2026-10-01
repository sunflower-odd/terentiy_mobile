package ru.terentiy.mobile.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import ru.terentiy.mobile.domain.Session
import timber.log.Timber

/**
 * Реализация [SecureStorage] поверх AndroidKeyStore и обычных SharedPreferences.
 *
 * Значения шифруются AES/GCM ключом, который не покидает keystore устройства.
 * Библиотека Jetpack Security (EncryptedSharedPreferences) сознательно не
 * используется: она помечена устаревшей, а нужный здесь объём — два значения —
 * умещается в один класс без внешней зависимости.
 *
 * Ключ привязан к устройству, поэтому перенос резервной копии на другое устройство
 * бесполезен. Соответственно, файл исключён из резервного копирования
 * (см. res/xml/backup_rules.xml). Если ключ окажется недоступен — например, после
 * смены способа блокировки экрана — значение считается утраченным и стирается:
 * приложение попросит войти заново, а не упадёт.
 *
 * Сессия хранится одной строкой с разделителем, а не двумя ключами: одно чтение
 * keystore вместо двух, и токен с идентификатором домохозяйства невозможно
 * получить по отдельности. Refresh-токена в реализованном контракте нет —
 * хранить нечего, кроме токена и household.
 */
@Singleton
class KeystoreSecureStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SecureStorage {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val lock = Any()

    override fun deviceId(): String = synchronized(lock) {
        read(KEY_DEVICE_ID) ?: newDeviceId()
    }

    /**
     * Выдаёт устройству новый идентификатор.
     *
     * Единственный способ пройти активацию заново: `POST /mobile/activate` с уже
     * известным `device_id` отвечает `409 device_already_activated`, а маршрута
     * сброса PIN или удаления устройства в контракте нет. Прежняя запись остаётся
     * на сервере навсегда — см. docs/backlog-mobile.md.
     *
     * Вызывается только из подтверждающего диалога: пользователь должен понимать,
     * что делает.
     */
    override fun rotateDeviceId(): String = synchronized(lock) {
        clearSession()
        newDeviceId()
    }

    private fun newDeviceId(): String =
        UUID.randomUUID().toString().also { write(KEY_DEVICE_ID, it) }

    override fun session(): Session? {
        synchronized(lock) {
        val raw = read(KEY_SESSION) ?: return null
        val parts = raw.split(SEPARATOR)
        if (parts.size != 2 || parts[0].isEmpty()) {
            Timber.w("Повреждённая запись сессии, стираю")
            clearSession()
            return null
        }
        return Session(accessToken = parts[0], householdId = parts[1])
        }
    }

    override fun saveSession(session: Session) = synchronized(lock) {
        write(KEY_SESSION, session.accessToken + SEPARATOR + session.householdId)
    }

    override fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private fun write(key: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val payload = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(1 + iv.size + payload.size)
            packed[0] = iv.size.toByte()
            iv.copyInto(packed, destinationOffset = 1)
            payload.copyInto(packed, destinationOffset = 1 + iv.size)
            prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }.onFailure {
            Timber.e(it, "Не удалось сохранить значение в защищённое хранилище | key=%s", key)
        }
    }

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val payload = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrElse {
            Timber.w(it, "Значение в защищённом хранилище не читается, стираю | key=%s", key)
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "terentiy_secure"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "terentiy_secure_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SESSION = "session"
        // Токен base64url-подобный, разделитель заведомо не встречается внутри него.
        const val SEPARATOR = "\u0000"
    }
}
