package com.example.espoti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.viewmodel.MeetingNotesViewModel

@Composable
fun MeetingNotesScreen(
    viewModel: MeetingNotesViewModel,
    onBackClick: () -> Unit
) {
    val content by viewModel.content
    val isLoading by viewModel.isLoading
    val isSaving by viewModel.isSaving
    val hasUnsavedChanges by viewModel.hasUnsavedChanges
    val message by viewModel.message
    val error by viewModel.error

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    fun handleBack() {
        if (isSaving) return

        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onBackClick()
        }
    }

    BackHandler(enabled = isSaving || hasUnsavedChanges) {
        handleBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = {
                Text("This note will be permanently deleted from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved changes") },
            text = {
                Text("Your changes haven't been saved. Leave anyway?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TextButton(onClick = ::handleBack) {
                Text("← Back", color = BrandBrown)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Meeting Notes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Keep your personal notes about this meeting.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandOrange)
                }
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = viewModel::updateContent,
                    label = { Text("Your notes") },
                    placeholder = {
                        Text("Write something about this meeting...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    enabled = !isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Notes are saved locally on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandBrown
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = viewModel::saveNote,
                    enabled = hasUnsavedChanges && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandOrange
                    )
                ) {
                    Text(if (isSaving) "Saving..." else "Save Note")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = content.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Note", color = BrandBrown)
                }

                if (hasUnsavedChanges) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You have unsaved changes.",
                        color = BrandBrown,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = BrandBrown
                    )
                }

                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
