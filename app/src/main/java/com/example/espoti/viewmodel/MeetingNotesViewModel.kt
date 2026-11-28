package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.local.MeetingNotesLocalStore
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MeetingNotesViewModel(
    private val meetingId: String,
    private val notesStore: MeetingNotesLocalStore =
        AppContainer.meetingNotesStore,
    private val currentUserId: () -> String? = {
        AuthRepository().currentUserId
    }
) : ViewModel() {

    private val _content = mutableStateOf("")
    val content: State<String> = _content

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving

    private val _hasUnsavedChanges = mutableStateOf(false)
    val hasUnsavedChanges: State<Boolean> = _hasUnsavedChanges

    private val _message = mutableStateOf<String?>(null)
    val message: State<String?> = _message

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    init {
        loadNote()
    }

    fun updateContent(newContent: String) {
        if (_isLoading.value) return

        _content.value = newContent
        _hasUnsavedChanges.value = true
        _message.value = null
        _error.value = null
    }

    private fun loadNote() {
        val uid = currentUserId()

        if (uid == null) {
            _isLoading.value = false
            _error.value = "You must be logged in to view notes."
            return
        }

        viewModelScope.launch {
            try {
                val note = notesStore.get(uid, meetingId)

                _content.value = note?.content.orEmpty()
                _hasUnsavedChanges.value = false

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _error.value = "Couldn't load your meeting note."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveNote() {
        if (_isLoading.value || _isSaving.value) return

        val uid = currentUserId()

        if (uid == null) {
            _error.value = "You must be logged in to save notes."
            return
        }

        val contentToSave = _content.value

        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _message.value = null

            try {
                notesStore.save(
                    ownerUid = uid,
                    meetingId = meetingId,
                    content = contentToSave
                )

                // Don't mark newer edits as saved.
                if (_content.value == contentToSave) {
                    _hasUnsavedChanges.value = false
                }

                _message.value = "Note saved on this device."

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _error.value = "Couldn't save your note."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteNote() {
        if (_isLoading.value || _isSaving.value) return

        val uid = currentUserId()

        if (uid == null) {
            _error.value = "You must be logged in to delete notes."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _message.value = null

            try {
                notesStore.delete(uid, meetingId)
                _content.value = ""
                _hasUnsavedChanges.value = false
                _message.value = "Note deleted."

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _error.value = "Couldn't delete your note."
            } finally {
                _isSaving.value = false
            }
        }
    }
}
