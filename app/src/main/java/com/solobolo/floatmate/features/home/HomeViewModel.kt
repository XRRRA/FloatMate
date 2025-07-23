package com.solobolo.floatmate.features.home

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.solobolo.floatmate.base.BaseViewModel
import com.solobolo.floatmate.service.FloatingBubbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.jvm.java

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : BaseViewModel<HomeContract.State, HomeContract.Event, HomeContract.Action>(
    HomeContract.State()
) {

    override suspend fun handleEvent(event: HomeContract.Event) {
        when (event) {
            is HomeContract.Event.Initialize -> initialize()
            is HomeContract.Event.ToggleService -> toggleService()
            is HomeContract.Event.NavigateToSettings -> {
                sendAction(HomeContract.Action.NavigateToSettings)
            }
        }
    }

    private fun initialize() {
        checkServiceStatus()
    }

    private fun checkServiceStatus() {
        val isRunning = FloatingBubbleService.isRunning
        updateState { it.copy(isServiceRunning = isRunning) }
    }

    private fun toggleService() {
        Log.d("zazavm", "toggleService() called")
        val currentState = state.value

        if (currentState.isServiceRunning) {
            stopService()
        } else {
            startService()
        }
    }

    private fun startService() {
        Log.d("zazavm", "startService() called")
        val intent = Intent(application, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("zazavm", "Using startForegroundService")
            application.startForegroundService(intent)
        } else {
            Log.d("zazavm", "Using startService")
            application.startService(intent)
        }
        updateState { it.copy(isServiceRunning = true) }
        sendAction(HomeContract.Action.ShowToast("FloatMate service started"))
    }

    private fun stopService() {
        Log.d("zazavm", "stopService() called")
        val intent = Intent(application, FloatingBubbleService::class.java)
        application.stopService(intent)
        updateState { it.copy(isServiceRunning = false) }
        sendAction(HomeContract.Action.ShowToast("FloatMate service stopped"))
    }

}