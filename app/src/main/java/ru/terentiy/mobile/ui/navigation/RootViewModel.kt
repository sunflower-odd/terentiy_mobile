package ru.terentiy.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.domain.SessionState

/**
 * Решает, с какого экрана начинается работа приложения, и куда уводить при
 * потере сессии.
 *
 * `null` означает «ещё не прочитали»: состояние собирается из настроек и
 * зашифрованного хранилища асинхронно, и до первого значения показывать что-либо
 * нельзя — иначе на старте мигнёт чужой экран.
 *
 * [SessionState.Active] означает лишь наличие токена. Действителен он или нет,
 * покажет первый же запрос: сервер отвечает `401`, если токен ему неизвестен, и
 * тогда [ru.terentiy.mobile.core.network.AuthorizedCall] стирает сессию, а это
 * состояние само переезжает в [SessionState.NeedsPin].
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState?> = auth.sessionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { auth.restoreServerUrl() }
    }
}
