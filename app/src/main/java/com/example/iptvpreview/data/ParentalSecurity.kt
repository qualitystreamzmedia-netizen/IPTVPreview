package com.example.iptvpreview.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PinHash {
    fun create(pin: String): String {
        require(pin.matches(Regex("[0-9]{4}")))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "120000:${hex(salt)}:${hex(derive(pin, salt, 120000))}"
    }
    fun verify(pin: String, stored: String): Boolean = try {
        val parts = stored.split(':')
        val rounds = parts[0].toInt()
        require(rounds in 100000..500000)
        MessageDigest.isEqual(unhex(parts[2]), derive(pin, unhex(parts[1]), rounds))
    } catch (_: Exception) { false }
    private fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

class ParentalSecurity(private val store: DataStore<Preferences>) {
    private val mutex = Mutex()
    private val pinKey = stringPreferencesKey("parental_pin_hash_v2")
    private val catsKey = stringSetPreferencesKey("locked_categories")
    private val channelsKey = stringSetPreferencesKey("locked_channels")
    private val failuresKey = intPreferencesKey("pin_failures")
    private val blockedUntilKey = longPreferencesKey("pin_blocked_until")
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()
    private val pinState = MutableStateFlow(false)
    val isPinSet = pinState.asStateFlow()
    private val catsState = MutableStateFlow<Set<String>>(emptySet())
    val lockedCategories = catsState.asStateFlow()
    private val channelState = MutableStateFlow<Set<String>>(emptySet())
    val lockedChannels = channelState.asStateFlow()

    suspend fun restore() = mutex.withLock { load() }
    private suspend fun load() {
        val prefs = store.data.first()
        pinState.value = !prefs[pinKey].isNullOrBlank()
        catsState.value = prefs[catsKey].orEmpty()
        channelState.value = prefs[channelsKey].orEmpty()
        readyState.value = true
    }
    fun isChannelLocked(channel: Channel) = !ready.value || channel.group in catsState.value || channel.id in channelState.value
    suspend fun verifyPin(pin: String): Boolean = mutex.withLock { verifyInternal(pin) }
    private suspend fun verifyInternal(pin: String): Boolean {
        val prefs = store.data.first()
        if (System.currentTimeMillis() < (prefs[blockedUntilKey] ?: 0L)) return false
        val hash = prefs[pinKey] ?: return false
        val valid = withContext(Dispatchers.Default) { pin.matches(Regex("[0-9]{4}")) && PinHash.verify(pin, hash) }
        store.edit {
            val failures = if (valid) 0 else (it[failuresKey] ?: 0) + 1
            it[failuresKey] = if (failures >= 5) 0 else failures
            it[blockedUntilKey] = if (failures >= 5) System.currentTimeMillis() + 30_000 else 0L
        }
        return valid
    }
    suspend fun setPin(pin: String, currentPin: String? = null): Boolean = mutex.withLock {
        load()
        if (isPinSet.value && (currentPin == null || !verifyInternal(currentPin))) return@withLock false
        val hash = withContext(Dispatchers.Default) { PinHash.create(pin) }
        store.edit { it[pinKey] = hash; it[failuresKey] = 0; it[blockedUntilKey] = 0L }
        load()
        true
    }
    suspend fun setCategoryLock(name: String, locked: Boolean, pin: String): Boolean = mutex.withLock {
        if (!verifyInternal(pin)) return@withLock false
        store.edit { prefs ->
            prefs[catsKey] = prefs[catsKey].orEmpty().toMutableSet().apply { if (locked) add(name) else remove(name) }
        }
        load()
        true
    }
}
