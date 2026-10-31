package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.model.Meeting
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.example.espoti.ui.components.MeetingArrivalStatus
import com.example.espoti.ui.components.MeetingLocationAccess
import com.example.espoti.ui.components.distanceLabel
import com.example.espoti.viewmodel.MeetingDetailViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import com.example.espoti.ui.components.RecommendationCard
import com.example.espoti.ui.components.VoteBar
import com.example.espoti.ui.components.VotingChip
import com.example.espoti.ui.components.formatDateTime
import com.example.espoti.ui.components.TransportFiltersPanel
import com.example.espoti.model.TransportFilterCalculator
import java.util.Locale
import com.example.espoti.ui.components.SavedDataCaption

/**
 * Detail of one Firestore meeting. While it is VOTING it shows the candidate
 * places with their votes and a fixed Vote bar ([onVoted] gets the meeting id
 * once the vote is saved); once decided it shows the place, travel status and
 * check-in.
 */
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit,
    onVoted: (meetingId: String) -> Unit
) {
    val meeting by viewModel.meeting
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val current = meeting

    when {
        current == null && isLoading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandBrown)
        }

        current == null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = error ?: "Meeting not found.", color = BrandBrown)
            Spacer(modifier = Modifier.height(12.dp))
            EspotiPrimaryButton(
                text = "Back",
                onClick = onBackClick,
                modifier = Modifier
                    .width(130.dp)
                    .height(42.dp)
            )
        }

        current.isVoting -> VotingDetail(
            meeting = current,
            viewModel = viewModel,
            onBackClick = onBackClick,
            onVoted = onVoted
        )

        else -> ConfirmedDetail(
            meeting = current,
            viewModel = viewModel,
            onBackClick = onBackClick
        )
    }
}

/** VOTING: pick one of the candidate places; Vote stays fixed at the bottom. */
@Composable
private fun VotingDetail(
    meeting: Meeting,
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit,
    onVoted: (String) -> Unit
) {
    val allCandidates by viewModel.candidates
    val candidates by viewModel.visibleCandidates
    val filters by viewModel.transportFilters
    val canUseFilters by viewModel.canUseTransportFilters
    val filterError by viewModel.transportFilterError
    val isLoading by viewModel.isLoading
    val voteCounts by viewModel.voteCounts
    val selectedPlaceId by viewModel.selectedPlaceId
    val isSaving by viewModel.isSavingVote
    val voteError by viewModel.voteError
    val isOnline by viewModel.isOnline
    val isCreator by viewModel.isCreator
    var showChooseDialog by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.placeId == selectedPlaceId }?.name

    // Organizer only: confirm before skipping the group voting.
    if (showChooseDialog && selectedName != null) {
        AlertDialog(
            onDismissRequest = { showChooseDialog = false },
            title = { Text("Choose for the group?") },
            text = {
                Text("$selectedName will be the place for everyone. Voting ends now and the other votes won't count.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showChooseDialog = false
                    viewModel.onChooseDirectly(onDone = onVoted)
                }) { Text("Choose", color = BrandOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showChooseDialog = false }) {
                    Text("Cancel", color = BrandBrown)
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCreator) {
                    OutlinedButton(
                        onClick = { showChooseDialog = true },
                        enabled = selectedPlaceId != null && !isSaving && !isLoading && isOnline,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Choose for the group", color = BrandBrown)
                    }
                }
                VoteBar(
                    canVote = selectedPlaceId != null &&
                            !isSaving &&
                            !isLoading &&
                            isOnline,
                    showHint = isOnline &&
                            selectedPlaceId == null &&
                            candidates.isNotEmpty(),
                    errorMessage = if (!isOnline) {
                        if (isSaving) {
                            "Connection lost. Your submitted vote is awaiting server confirmation."
                        } else {
                            "You're offline. You can browse and filter saved places. Reconnect to confirm your vote."
                        }
                    } else {
                        voteError
                    },
                    onVote = {
                        viewModel.onVoteConfirmed(onDone = onVoted)
                    },
                    buttonText = when {
                        isSaving -> "Saving..."
                        !isOnline -> "Connect to vote"
                        meeting.hasVoted -> "Update vote"
                        else -> "Vote"
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBrown
                )
                Text(
                    text = "✕",
                    color = BrandBrown,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            VotingChip()
            Spacer(modifier = Modifier.height(10.dp))
            MeetingDetailDataNotice(viewModel)

            meeting.startsAtEpochMillis?.let { start ->
                Text(text = "Meeting: ${formatDateTime(start)}", color = BrandBrown)
            }
            meeting.votingDeadline?.let { deadline ->
                Text(text = "Voting closes ${formatDateTime(deadline)}", color = BrandBrown)
            }
            Text(
                text = "${meeting.voteCount} of ${meeting.participantCount} voted",
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(18.dp))

            TransportFiltersPanel(
                filters = filters,
                hasStartingLocation = canUseFilters,
                isBusy = isSaving || isLoading,
                totalCount = allCandidates.size,
                visibleCount = candidates.size,
                unknownDistanceCount = allCandidates.count {
                    it.distanceKm == null
                },
                error = filterError,
                onApply = viewModel::applyTransportFilters,
                onClear = viewModel::clearTransportFilters
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Vote for a place:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(color = BrandBrown)
                }

                allCandidates.isEmpty() -> {
                    Text(
                        text = "No candidate places are available.",
                        color = BrandBrown
                    )
                }

                candidates.isEmpty() -> {
                    Text(
                        text = "No places match these filters. " +
                                "Try wider limits or clear the filters.",
                        color = BrandBrown
                    )
                }
            }

            candidates.forEach { place ->
                val votes = voteCounts[place.placeId] ?: 0
                val distance = place.distanceKm
                val minutes = TransportFilterCalculator.walkingMinutes(distance)

                val transportText =
                    if (distance != null && minutes != null) {
                        String.format(
                            Locale.US,
                            "%.2f km · ~%d min walking",
                            distance,
                            minutes
                        )
                    } else {
                        "Distance unavailable"
                    }

                val votesText = if (votes == 1) "1 vote" else "$votes votes"
                RecommendationCard(
                    title = place.name,
                    rating = place.rating,
                    subtitle = "$transportText · $votesText",
                    isSelected = selectedPlaceId == place.placeId,
                    onClick = { viewModel.onPlaceSelected(place.placeId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** Decided meeting: place, travel status and check-in (the original detail). */
@Composable
private fun ConfirmedDetail(
    meeting: Meeting,
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit
) {
    val status = viewModel.contexts[meeting.id]
    val scope = rememberCoroutineScope()
    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clickable{ onBackClick()}
                .padding(horizontal = 4.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBrown
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "↗",
                        color = BrandBrown
                    )

                    Text(
                        text = "✕",
                        color = BrandBrown,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            MeetingDetailDataNotice(viewModel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "★".repeat(meeting.rating),
                    color = BrandOrange
                )

                Text(
                    text = status?.let {
                        "~${it.walkingMinutes} min walking"
                    } ?: "Location needed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = status?.distanceLabel() ?: "Location needed",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfacePeach),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MAP",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (meeting.latitude != null && meeting.startsAtEpochMillis != null) {
                MeetingLocationAccess(
                    viewModel = viewModel,
                    meetings = listOf(meeting)
                )
            }

            meeting.startsAtEpochMillis?.let { start ->
                Text(text = "Meeting: ${formatDateTime(start)}")
            }

            MeetingArrivalStatus(
                status = status,
                loading = viewModel.loading,
                onCheckIn = {
                    scope.launch {
                        viewModel.refreshContext(
                            meetings = listOf(meeting),
                            checkIn = meeting
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Travel time",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Text(
                text = status?.let {
                    "~${it.walkingMinutes} minutes walking"
                } ?: "Not available",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "${meeting.participantCount} going:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(meeting.participantCount.coerceAtMost(5)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrandBrown)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BrandOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EspotiPrimaryButton(
                    text = "Take a photo",
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    containerColor = BrandOrange
                )
                EspotiPrimaryButton(
                    text = "Cancel",
                    onClick = onBackClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                )
            }
        }
    }
}
/** Explains whether the detail is showing saved data or a refresh failed. */
@Composable
private fun MeetingDetailDataNotice(
    viewModel: MeetingDetailViewModel
) {
    val isOnline by viewModel.isOnline
    val savedDataNotice by viewModel.savedDataNotice
    val error by viewModel.error
    val isLoading by viewModel.isLoading

    val message = error
        ?: savedDataNotice
        ?: if (!isOnline) {
            "You're offline. Reconnect to refresh this meeting and confirm a vote."
        } else {
            null
        }

    message?.let {
        SavedDataCaption(
            text = it,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isOnline && !isLoading) {
            androidx.compose.material3.TextButton(
                onClick = viewModel::load
            ) {
                Text("Refresh meeting")
            }
        }
    }
}