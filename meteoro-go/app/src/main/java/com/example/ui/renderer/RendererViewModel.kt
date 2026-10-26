package com.example.ui.renderer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.MeteoroApp
import com.example.network.MeteoroRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RendererUiState {
    data object Loading : RendererUiState
    data class Success(
        val app: MeteoroApp,
        val rawJson: String,
        val url: String,
        val lastUpdated: Long = System.currentTimeMillis(),
        val isLiveReloadActive: Boolean = true
    ) : RendererUiState
    data class Error(
        val message: String,
        val url: String,
        val canLoadDemo: Boolean = true
    ) : RendererUiState
}

class RendererViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MeteoroRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<RendererUiState>(RendererUiState.Loading)
    val uiState: StateFlow<RendererUiState> = _uiState.asStateFlow()

    private val _inputValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val inputValues: StateFlow<Map<String, String>> = _inputValues.asStateFlow()

    private val _interactiveCounter = MutableStateFlow(0)
    val interactiveCounter: StateFlow<Int> = _interactiveCounter.asStateFlow()

    private val _isLiveReloading = MutableStateFlow(true)
    val isLiveReloading: StateFlow<Boolean> = _isLiveReloading.asStateFlow()

    private var pollingJob: Job? = null
    private var currentTargetUrl: String = ""
    private var lastKnownContentHash: Int = 0
    private var demoVersionCounter: Int = 1

    fun loadApp(url: String) {
        currentTargetUrl = url
        _isLiveReloading.value = supportsLiveReload(url)
        _uiState.value = RendererUiState.Loading
        fetchCurrentSchema(initialLoad = true)
        if (_isLiveReloading.value) startLiveReloadPolling()
    }

    fun refreshManually() {
        viewModelScope.launch {
            demoVersionCounter++
            fetchCurrentSchema(initialLoad = false)
        }
    }

    fun toggleLiveReload(enable: Boolean? = null) {
        val newState = (enable ?: !_isLiveReloading.value) && supportsLiveReload(currentTargetUrl)
        _isLiveReloading.value = newState
        if (newState) {
            startLiveReloadPolling()
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun setInputValue(componentId: String, text: String) {
        _inputValues.update { current ->
            current + (componentId to text)
        }
    }

    fun incrementCounter() {
        _interactiveCounter.update { it + 1 }
    }

    fun decrementCounter() {
        _interactiveCounter.update { it - 1 }
    }

    fun loadFallbackDemo(key: String = "coffee") {
        _isLiveReloading.value = false
        pollingJob?.cancel()
        pollingJob = null
        val demoApp = repository.getBuiltInDemo(key, demoVersionCounter)
        currentTargetUrl = "demo:$key"
        _uiState.value = RendererUiState.Success(
            app = demoApp,
            rawJson = "Built-in demo: $key",
            url = currentTargetUrl,
            lastUpdated = System.currentTimeMillis(),
            isLiveReloadActive = _isLiveReloading.value
        )
    }

    private fun fetchCurrentSchema(initialLoad: Boolean) {
        viewModelScope.launch {
            val result = repository.fetchAppSchema(currentTargetUrl)
            result.onSuccess { (app, rawJson) ->
                val contentHash = rawJson.hashCode()
                if (initialLoad || contentHash != lastKnownContentHash) {
                    lastKnownContentHash = contentHash
                    _uiState.value = RendererUiState.Success(
                        app = app,
                        rawJson = rawJson,
                        url = currentTargetUrl,
                        lastUpdated = System.currentTimeMillis(),
                        isLiveReloadActive = _isLiveReloading.value
                    )
                }
            }.onFailure { error ->
                if (initialLoad) {
                    _uiState.value = RendererUiState.Error(
                        message = error.localizedMessage ?: "No se pudo conectar al servidor de Meteoro.",
                        url = currentTargetUrl
                    )
                }
            }
        }
    }

    private fun startLiveReloadPolling() {
        if (!supportsLiveReload(currentTargetUrl)) return
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _isLiveReloading.value) {
                delay(2000) // Poll server every 2 seconds
                if (currentTargetUrl.isNotEmpty() && !currentTargetUrl.startsWith("demo:")) {
                    val result = repository.fetchAppSchema(currentTargetUrl)
                    result.onSuccess { (app, rawJson) ->
                        val contentHash = rawJson.hashCode()
                        if (contentHash != lastKnownContentHash) {
                            lastKnownContentHash = contentHash
                            _uiState.update { current ->
                                if (current is RendererUiState.Success) {
                                    current.copy(
                                        app = app,
                                        rawJson = rawJson,
                                        lastUpdated = System.currentTimeMillis()
                                    )
                                } else {
                                    RendererUiState.Success(
                                        app = app,
                                        rawJson = rawJson,
                                        url = currentTargetUrl,
                                        lastUpdated = System.currentTimeMillis(),
                                        isLiveReloadActive = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private fun supportsLiveReload(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
