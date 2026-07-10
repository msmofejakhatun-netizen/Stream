package com.example.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePreferences {
    private const val PREFS_NAME = "secure_streamplay_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"

    private fun getSharedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(context: Context, token: String?) {
        getSharedPrefs(context).edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? {
        return getSharedPrefs(context).getString(KEY_JWT_TOKEN, null)
    }
}
