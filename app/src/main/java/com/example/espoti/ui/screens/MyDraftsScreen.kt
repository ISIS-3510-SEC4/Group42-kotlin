
package com.example.espoti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.data.local.MeetingDraft
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.viewmodel.MeetingDraftsViewModel

@Composable
fun MyDraftsScreen(
    onContinueEditing: (String) -> Unit,
    viewModel: MeetingDraftsViewModel = viewModel()
) {
    val drafts by viewModel.drafts
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    var draftToDelete by remember {
        mutableStateOf<MeetingDraft?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(15.dp))

        CreateDraftsHeader()

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = "My Drafts",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.padding(8.dp))

        when {
            isLoading && drafts.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            error != null && drafts.isEmpty() -> {
                Text(
                    text = error ?: "Something went wrong.",
                    color = MaterialTheme.colorScheme.error
                )

                TextButton(onClick = { viewModel.loadDrafts() }) {
                    Text("Try again")
                }
            }

            drafts.isEmpty() -> {
                Text(
                    text = "You don't have any saved drafts yet.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else -> {
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = drafts,
                        key = { it.id }
                    ) { draft ->
                        MeetingDraftCard(
                            draft = draft,
                            onContinue = {
                                onContinueEditing(draft.id)
                            },
                            onDelete = {
                                draftToDelete = draft
                            }
                        )
                    }
                }
            }
        }
    }

    draftToDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { draftToDelete = null },
            title = {
                Text("Delete draft?")
            },
            text = {
                Text("This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draft.id)
                        draftToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { draftToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MeetingDraftCard(
    draft: MeetingDraft,
    onContinue: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = draft.whatToDo.ifBlank { "Untitled meeting" },
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Date: ${draft.date ?: "Not selected"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Time: ${draft.time ?: "Not selected"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Last updated: ${java.text.SimpleDateFormat(
                    "MMM d, yyyy · h:mm a",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(draft.updatedAt))}",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Continue editing")
                }

                OutlinedButton(
                    onClick = onDelete
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
@Composable
private fun CreateDraftsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EspotiLogo(size = 55.dp)
        Text(
            text = "☰",
            style = MaterialTheme.typography.headlineMedium,
            color=BrandBrown
        )
    }
}