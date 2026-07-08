package com.example.repository

import android.util.Log
import com.example.models.UserProfile
import com.example.network.AuthRequest
import com.example.network.GoogleAuthRequest
import com.example.network.StreamPlayRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuthRepository {
    private val _currentUser = MutableStateFlow<UserProfile?>(
        UserProfile(
            id = "GUEST_123",
            email = "guest@streamplay.com",
            displayName = "Guest Viewer",
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80",
            isGuest = true
        )
    )
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    suspend fun loginWithEmail(email: String, name: String) {
        try {
            // Attempt register first, if fails because exists, perform login
            val api = StreamPlayRetrofitClient.service
            val response = try {
                api.register(AuthRequest(email = email, password = "streamplay_secure_pass_2026", displayName = name))
            } catch (e: Exception) {
                Log.w("AuthRepository", "Register failed (user might exist), trying login: ${e.message}")
                api.login(AuthRequest(email = email, password = "streamplay_secure_pass_2026"))
            }
            StreamPlayRetrofitClient.setAuthToken(response.token)
            _currentUser.value = response.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "REST email login failed. Using local fallback: ${e.message}")
            // Graceful safe fallback to preserve offline functionality
            val user = UserProfile(
                id = "u_" + UUID.randomUUID().toString().take(6),
                email = email,
                displayName = name,
                avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=120&q=80",
                isGuest = false,
                joinedDate = "Jul 2026",
                subscribersCount = 42
            )
            _currentUser.value = user
        }
    }

    suspend fun loginWithGoogle() {
        try {
            val response = StreamPlayRetrofitClient.service.loginWithGoogle(
                GoogleAuthRequest(
                    email = "google.user@gmail.com",
                    displayName = "Alex Rivers",
                    avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80"
                )
            )
            StreamPlayRetrofitClient.setAuthToken(response.token)
            _currentUser.value = response.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "REST google login failed. Using local fallback: ${e.message}")
            val user = UserProfile(
                id = "GOOGLE_USER_456",
                email = "google.user@gmail.com",
                displayName = "Alex Rivers",
                avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80",
                isGuest = false,
                joinedDate = "Jul 2026",
                subscribersCount = 1250
            )
            _currentUser.value = user
        }
    }

    fun logout() {
        StreamPlayRetrofitClient.setAuthToken(null)
        _currentUser.value = null
    }

    suspend fun continueAsGuest() {
        try {
            val response = StreamPlayRetrofitClient.service.loginAsGuest()
            StreamPlayRetrofitClient.setAuthToken(response.token)
            _currentUser.value = response.user
        } catch (e: Exception) {
            Log.e("AuthRepository", "REST guest login failed. Using local fallback: ${e.message}")
            _currentUser.value = UserProfile(
                id = "GUEST_" + UUID.randomUUID().toString().take(6),
                email = "guest@streamplay.com",
                displayName = "Guest Viewer",
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80",
                isGuest = true
            )
        }
    }
}

