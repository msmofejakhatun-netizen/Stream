package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.UserProfile
import com.example.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun loginWithEmail(email: String, name: String) {
        viewModelScope.launch {
            authRepository.loginWithEmail(email, name)
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            authRepository.loginWithGoogle()
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            authRepository.continueAsGuest()
        }
    }
}
