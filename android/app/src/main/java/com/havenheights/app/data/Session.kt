package com.havenheights.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.GeneralSecurityException

/** Sign-in token and profile, encrypted with a key held in the Android Keystore. */
class SessionStore(private val context: Context) {
    private val prefs: SharedPreferences = open()

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: GeneralSecurityException) {
        // The Keystore key was lost (restored backup, security reset). Start clean; the user signs in again.
        File(context.applicationInfo.dataDir, "shared_prefs/$FILE.xml").delete()
        create()
    }

    private fun create(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val token: String? get() = prefs.getString("token", null)

    var pushToken: String?
        get() = prefs.getString("push_token", null)
        set(value) = prefs.edit().putString("push_token", value).apply()

    fun user(): UserInfo? {
        if (token == null) return null
        val tenancy = prefs.getInt("tenancy_id", -1).takeIf { it >= 0 }
        return UserInfo(prefs.getString("role", "") ?: "", prefs.getString("name", "") ?: "", tenancy)
    }

    fun save(auth: AuthResponse) {
        prefs.edit()
            .putString("token", auth.accessToken)
            .putString("role", auth.role)
            .putString("name", auth.name)
            .putInt("tenancy_id", auth.tenancyId ?: -1)
            .apply()
    }

    fun updateName(name: String) = prefs.edit().putString("name", name).apply()

    fun clear() {
        val push = pushToken
        prefs.edit().clear().apply()
        pushToken = push   // the phone's push token is not tied to a user
    }

    private companion object { const val FILE = "haven_session_secure" }
}

/** Identifies who queued an upload, so it is never sent under someone else's sign-in. */
fun ownerKey(user: UserInfo) = "${user.role}:${user.tenancyId ?: 0}"
