package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.data.local.MeetingDraft
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.MeetingDraftsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button


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

    Scaffold { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Header
            item(key = "header") {
                Column {
                    CreateDraftsHeader()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Screen title
            item(key = "title") {
                Column {
                    Text(
                        text = "My Drafts",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandBrown
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Loading state
            if (isLoading && drafts.isEmpty()) {
                item(key = "loading") {
                    CircularProgressIndicator(
                        color = BrandBrown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .wrapContentSize(Alignment.Center)
                    )
                }
            }

            // Error state
            if (error != null && drafts.isEmpty() && !isLoading) {
                item(key = "error") {
                    Column {
                        Text(
                            text = error ?: "Something went wrong.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        EspotiPrimaryButton(
                            text = "Try again",
                            onClick = { viewModel.loadDrafts() }
                        )
                    }
                }
            }

            // Empty state
            if (drafts.isEmpty() && !isLoading && error == null) {
                item(key = "empty") {
                    Text(
                        text = "You don't have any saved drafts yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrandBrown
                    )
                }
            }

            // Error while displaying cached drafts
            if (error != null && drafts.isNotEmpty()) {
                item(key = "refresh-error") {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Draft list
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

    // Delete confirmation
    draftToDelete?.let { draft ->

        AlertDialog(
            onDismissRequest = {
                draftToDelete = null
            },
            title = {
                Text(
                    text = "Delete draft?",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBrown
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draft.id)
                        draftToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        draftToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = BrandBrown
                    )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfacePeach)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // Draft title
        Text(
            text = draft.whatToDo.ifBlank { "Untitled meeting" },
            style = MaterialTheme.typography.titleMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Date
        Text(
            text = "Date: ${draft.date ?: "Not selected"}",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        // Time
        Text(
            text = "Time: ${draft.time ?: "Not selected"}",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Last updated
        Text(
            text = "Last updated: ${
                remember(draft.updatedAt) {
                    SimpleDateFormat(
                        "MMM d, yyyy · h:mm a",
                        Locale.getDefault()
                    ).format(Date(draft.updatedAt))
                }
            }",
            style = MaterialTheme.typography.labelSmall,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            EspotiPrimaryButton(
                text = "Continue editing",
                onClick = onContinue,
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
            )

            Button(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandOrange,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge
                )
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
            color = BrandBrown
        )
    }
}
