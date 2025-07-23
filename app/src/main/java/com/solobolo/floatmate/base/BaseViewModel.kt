package com.solobolo.floatmate.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseViewModel<State, Event, Action>(initialState: State) : ViewModel() {

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    private val _event: Channel<Event> = Channel(Channel.UNLIMITED)
    private val _action = MutableSharedFlow<Action>()

    val action: SharedFlow<Action> = _action
    val state = _state.asStateFlow()

    fun sendEvent(e: Event, context: CoroutineContext = EmptyCoroutineContext) {
        viewModelScope.launch(context = context) { _event.send(e) }
    }

    fun sendAction(a: Action, context: CoroutineContext = EmptyCoroutineContext) {
        viewModelScope.launch(context = context) { _action.emit(a) }
    }

    protected fun updateState(morph: (State) -> State) {
        _state.update(morph)
    }

    abstract suspend fun handleEvent(event: Event)

    init {
        viewModelScope.launch { _event.consumeAsFlow().collect(::handleEvent) }
    }
}
