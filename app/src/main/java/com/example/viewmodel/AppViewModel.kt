package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.UserEntity
import com.example.data.remote.GeminiClient
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val userDao = db.userDao()
    private val profileDao = db.businessProfileDao()
    private val chatDao = db.chatMessageDao()
    private val prefs = application.getSharedPreferences("biz_consultant_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Business Profile Flow reactively linked to logged-in user
    val currentProfile: StateFlow<BusinessProfileEntity?> = _currentUser.flatMapLatest { user ->
        if (user != null) {
            profileDao.getProfileForUser(user.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat Messages Flow reactively linked to logged-in user
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _currentUser.flatMapLatest { user ->
        if (user != null) {
            chatDao.getMessagesForUser(user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // Image analysis state
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedImageBitmap: StateFlow<Bitmap?> = _selectedImageBitmap.asStateFlow()

    private val _isImageAnalyzing = MutableStateFlow(false)
    val isImageAnalyzing: StateFlow<Boolean> = _isImageAnalyzing.asStateFlow()

    private val _imageAnalysisResult = MutableStateFlow<String?>(null)
    val imageAnalysisResult: StateFlow<String?> = _imageAnalysisResult.asStateFlow()

    private val _imageAnalysisError = MutableStateFlow<String?>(null)
    val imageAnalysisError: StateFlow<String?> = _imageAnalysisError.asStateFlow()

    init {
        // Restore session if user was previously logged in
        val savedUserId = prefs.getLong("logged_in_user_id", -1L)
        if (savedUserId != -1L) {
            viewModelScope.launch {
                userDao.getUserById(savedUserId).collect { user ->
                    _currentUser.value = user
                }
            }
        }
    }

    fun clearAuthMessages() {
        _authError.value = null
        _authSuccessMessage.value = null
    }

    fun register(name: String, email: String, pass: String, onSuccess: () -> Unit) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim().lowercase()
        val trimmedPass = pass.trim()

        if (trimmedName.isBlank() || trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            _authError.value = "Please fill in all fields (Name, Email, and Password)."
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _authError.value = "Please enter a valid email address."
            return
        }

        if (trimmedPass.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return
        }

        _isAuthLoading.value = true
        _authError.value = null

        viewModelScope.launch {
            try {
                val existing = userDao.getUserByEmail(trimmedEmail)
                if (existing != null) {
                    _authError.value = "An account with this email already exists. Please log in."
                    _isAuthLoading.value = false
                    return@launch
                }

                val hashed = SecurityUtils.hashPassword(trimmedPass)
                val newUser = UserEntity(
                    name = trimmedName,
                    email = trimmedEmail,
                    passwordHash = hashed
                )
                val newId = userDao.insertUser(newUser)
                val createdUser = newUser.copy(id = newId)

                // Initialize default profile
                profileDao.insertOrUpdate(
                    BusinessProfileEntity(
                        userId = newId,
                        businessName = "${trimmedName}'s Venture",
                        industry = "Retail / Services",
                        budget = "$10,000",
                        goals = "Expand customer base & boost revenue",
                        targetCustomers = "Local consumers and online shoppers"
                    )
                )

                _currentUser.value = createdUser
                prefs.edit().putLong("logged_in_user_id", newId).apply()
                _authSuccessMessage.value = "Account created successfully!"
                _isAuthLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _authError.value = "Registration error: ${e.message}"
                _isAuthLoading.value = false
            }
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPass = pass.trim()

        if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            _authError.value = "Please enter both email and password."
            return
        }

        _isAuthLoading.value = true
        _authError.value = null

        viewModelScope.launch {
            try {
                val user = userDao.getUserByEmail(trimmedEmail)
                if (user == null) {
                    _authError.value = "No account found with this email. Please check your credentials or register."
                    _isAuthLoading.value = false
                    return@launch
                }

                val isValid = SecurityUtils.verifyPassword(trimmedPass, user.passwordHash)
                if (!isValid) {
                    _authError.value = "Incorrect password. Please try again."
                    _isAuthLoading.value = false
                    return@launch
                }

                _currentUser.value = user
                prefs.edit().putLong("logged_in_user_id", user.id).apply()
                _isAuthLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _authError.value = "Login error: ${e.message}"
                _isAuthLoading.value = false
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        prefs.edit().remove("logged_in_user_id").apply()
        _currentUser.value = null
        _chatError.value = null
        _imageAnalysisResult.value = null
        _selectedImageBitmap.value = null
        _selectedImageUri.value = null
        onLoggedOut()
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key.trim()
        prefs.edit().putString("custom_api_key", key.trim()).apply()
    }

    fun saveBusinessProfile(
        businessName: String,
        industry: String,
        budget: String,
        goals: String,
        targetCustomers: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        if (businessName.isBlank()) {
            onComplete(false, "Business name cannot be empty.")
            return
        }

        viewModelScope.launch {
            try {
                val entity = BusinessProfileEntity(
                    userId = user.id,
                    businessName = businessName.trim(),
                    industry = industry.trim(),
                    budget = budget.trim(),
                    goals = goals.trim(),
                    targetCustomers = targetCustomers.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                profileDao.insertOrUpdate(entity)
                onComplete(true, "Business profile saved successfully!")
            } catch (e: Exception) {
                onComplete(false, "Failed to save profile: ${e.message}")
            }
        }
    }

    fun sendConsultantMessage(question: String) {
        val user = _currentUser.value ?: return
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isBlank()) return

        _isChatLoading.value = true
        _chatError.value = null

        viewModelScope.launch {
            try {
                // Save user question to DB
                chatDao.insertMessage(
                    ChatMessageEntity(
                        userId = user.id,
                        sender = "USER",
                        content = trimmedQuestion
                    )
                )

                val profile = profileDao.getProfileSync(user.id)
                val contextStr = profile?.let {
                    "Business: ${it.businessName}\nIndustry: ${it.industry}\nBudget: ${it.budget}\nGoals: ${it.goals}\nTarget Audience: ${it.targetCustomers}"
                }

                val key = GeminiClient.getResolvedApiKey(_customApiKey.value)
                val result = GeminiClient.generateTextResponse(
                    prompt = trimmedQuestion,
                    businessContext = contextStr,
                    apiKey = key
                )

                result.fold(
                    onSuccess = { reply ->
                        chatDao.insertMessage(
                            ChatMessageEntity(
                                userId = user.id,
                                sender = "AI",
                                content = reply
                            )
                        )
                        _isChatLoading.value = false
                    },
                    onFailure = { err ->
                        _chatError.value = err.message ?: "Failed to generate AI consultation reply."
                        _isChatLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _chatError.value = "Chat error: ${e.message}"
                _isChatLoading.value = false
            }
        }
    }

    fun clearChat() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            chatDao.clearChat(user.id)
        }
    }

    fun setImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        _imageAnalysisResult.value = null
        _imageAnalysisError.value = null
        if (uri == null) {
            _selectedImageBitmap.value = null
            return
        }
        try {
            val context = getApplication<Application>().applicationContext
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            _selectedImageBitmap.value = bitmap
        } catch (e: Exception) {
            _imageAnalysisError.value = "Failed to load selected image: ${e.message}"
        }
    }

    fun analyzeSelectedImage(prompt: String) {
        val bitmap = _selectedImageBitmap.value
        if (bitmap == null) {
            _imageAnalysisError.value = "Please select an image first."
            return
        }

        val user = _currentUser.value
        _isImageAnalyzing.value = true
        _imageAnalysisError.value = null
        _imageAnalysisResult.value = null

        viewModelScope.launch {
            try {
                val profile = user?.let { profileDao.getProfileSync(it.id) }
                val contextStr = profile?.let {
                    "Business: ${it.businessName}\nIndustry: ${it.industry}\nBudget: ${it.budget}\nGoals: ${it.goals}\nTarget Audience: ${it.targetCustomers}"
                }

                val key = GeminiClient.getResolvedApiKey(_customApiKey.value)
                val result = GeminiClient.analyzeImage(
                    bitmap = bitmap,
                    userPrompt = prompt,
                    businessContext = contextStr,
                    apiKey = key
                )

                result.fold(
                    onSuccess = { reply ->
                        _imageAnalysisResult.value = reply
                        _isImageAnalyzing.value = false
                    },
                    onFailure = { err ->
                        _imageAnalysisError.value = err.message ?: "Analysis failed."
                        _isImageAnalyzing.value = false
                    }
                )
            } catch (e: Exception) {
                _imageAnalysisError.value = "Analysis error: ${e.message}"
                _isImageAnalyzing.value = false
            }
        }
    }
}
