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
import ru.terentiy.mobile.domain.Session

/**
 * Решает, с какого экрана начинается работа приложения.
 *
 * Состояние `Active` означает лишь наличие сохранённого токена. Действителен он
 * или нет, покажет первый же запрос к API: механики проверки токена у backend нет,
 * а гонять лишний запрос ради проверки бессмысленно — главный экран всё равно
 * обращается к `GET /api/v1/agents` сразу после открытия.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { auth.restoreServerUrl() }
    }

    fun dropToken() = auth.dropToken()
}
