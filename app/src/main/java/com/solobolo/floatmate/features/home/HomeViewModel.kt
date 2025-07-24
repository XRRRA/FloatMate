package com.solobolo.floatmate.features.home

import android.app.Application
import android.content.Intent
import android.os.Build
import com.solobolo.floatmate.base.BaseViewModel
import com.solobolo.floatmate.service.FloatingBubbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : BaseViewModel<HomeContract.State, HomeContract.Event, HomeContract.Action>(
    HomeContract.State()
) {

    companion object {
        var instance: HomeViewModel? = null
            private set
    }

    init {
        instance = this
    }

    override fun onCleared() {
        super.onCleared()
        instance = null
    }

    override suspend fun handleEvent(event: HomeContract.Event) {
        when (event) {
            is HomeContract.Event.Initialize -> checkServiceStatus()
            is HomeContract.Event.ToggleService -> toggleService()
            is HomeContract.Event.NavigateToSettings -> {
                sendAction(HomeContract.Action.NavigateToSettings)
            }
        }
    }

    private fun checkServiceStatus() {
        val isRunning = FloatingBubbleService.isRunning
        updateState { it.copy(isServiceRunning = isRunning) }
    }

    private fun toggleService() {
        val currentState = state.value

        if (currentState.isServiceRunning) {
            stopService()
        } else {
            startService()
        }
    }

    private fun startService() {
        val intent = Intent(application, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
        }
        updateState { it.copy(isServiceRunning = true) }
    }

    private fun stopService() {
        val intent = Intent(application, FloatingBubbleService::class.java)
        application.stopService(intent)
        updateState { it.copy(isServiceRunning = false) }
    }

    fun onBubbleDeleted() {
        updateState { it.copy(isServiceRunning = false) }
    }
}