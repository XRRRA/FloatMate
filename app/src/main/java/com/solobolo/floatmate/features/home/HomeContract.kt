package com.solobolo.floatmate.features.home

interface HomeContract {
    data class State(
        val isServiceRunning: Boolean = false,
        val isLoading: Boolean = false,
        val hasOverlayPermission: Boolean = false,
        val hasNotificationPermission: Boolean = false,
    )

    sealed interface Event {
        data object Initialize : Event
        data object ToggleService : Event
        data object NavigateToSettings : Event
    }

    sealed interface Action {
        data class ShowToast(val message: String) : Action
        data object NavigateToSettings : Action
    }
}