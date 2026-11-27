
package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.local.MeetingDraft
import com.example.espoti.data.local.MeetingDraftStore
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MeetingDraftsViewModel(
    private val meetingDraftStore: MeetingDraftStore =
        AppContainer.meetingDraftStore,
    private val currentUserId: () -> String? =
        { AuthRepository().currentUserId }
) : ViewModel() {

    private val _drafts = mutableStateOf<List<MeetingDraft>>(emptyList())
    val drafts: State<List<MeetingDraft>> = _drafts

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isDeleting = mutableStateOf(false)
    val isDeleting: State<Boolean> = _isDeleting

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        val uid = currentUserId() ?: run {
            _drafts.value = emptyList()
            _error.value = "You need to sign in to see your drafts."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                _drafts.value = meetingDraftStore.getDrafts(uid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _error.value = "Couldn't load your meeting drafts."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteDraft(draftId: String) {
        if (_isDeleting.value) return

        val uid = currentUserId() ?: return

        viewModelScope.launch {
            _isDeleting.value = true
            _error.value = null

            try {
                meetingDraftStore.delete(
                    id = draftId,
                    ownerUid = uid
                )

                _drafts.value = meetingDraftStore.getDrafts(uid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _error.value = "Couldn't delete this draft. Please try again."
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
