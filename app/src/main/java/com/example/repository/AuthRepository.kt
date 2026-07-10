package com.example.repository

import android.content.Context
import android.util.Log
import com.example.models.UserProfile
import com.example.network.AuthRequest
import com.example.network.GoogleAuthRequest
import com.example.network.SecurePreferences
import com.example.network.StreamPlayRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuthRepository(private val context: Context) {
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    suspend fun checkSession() {
        val savedToken = SecurePreferences.getToken(context)
        if (!savedToken.isNullOrBlank()) {
            StreamPlayRetrofitClient.setAuthToken(savedToken)
            try {
                // If there's an API to get current profile, fetch it.
                // In StreamPlayApiService we have getProfile() or getCreatorChannel() or we can just verify the token.
                // Let's call getProfile if defined, or assume success and fetch from token decrypter or standard REST call.
                val response = StreamPlayRetrofitClient.service.getProfile()
                _currentUser.value = response
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed to auto-login via saved JWT: ${e.message}")
                SecurePreferences.saveToken(context, null)
                StreamPlayRetrofitClient.setAuthToken(null)
                _currentUser.value = null
            }
        }
    }

    suspend fun loginWithEmail(email: String, name: String) {
        // Attempt register first, if fails because exists, perform login
        val api = StreamPlayRetrofitClient.service
        val response = try {
            api.register(AuthRequest(email = email, password = "streamplay_secure_pass_2026", displayName = name))
        } catch (e: Exception) {
            Log.w("AuthRepository", "Register failed (user might exist), trying login: ${e.message}")
            api.login(AuthRequest(email = email, password = "streamplay_secure_pass_2026"))
        }
        StreamPlayRetrofitClient.setAuthToken(response.token)
        SecurePreferences.saveToken(context, response.token)
        _currentUser.value = response.user
    }

    suspend fun loginWithGoogle() {
        val response = StreamPlayRetrofitClient.service.loginWithGoogle(
            GoogleAuthRequest(
                email = "google.user@gmail.com",
                displayName = "Alex Rivers",
                avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80"
            )
        )
        StreamPlayRetrofitClient.setAuthToken(response.token)
        SecurePreferences.saveToken(context, response.token)
        _currentUser.value = response.user
    }

    fun logout() {
        StreamPlayRetrofitClient.setAuthToken(null)
        SecurePreferences.saveToken(context, null)
        _currentUser.value = null
    }

    suspend fun continueAsGuest() {
        val response = StreamPlayRetrofitClient.service.loginAsGuest()
        StreamPlayRetrofitClient.setAuthToken(response.token)
        SecurePreferences.saveToken(context, response.token)
        _currentUser.value = response.user
    }
}
