package com.example.espoti.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingTab
import com.example.espoti.ui.components.CanceledMeetingCard
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.components.MeetingLocationAccess
import com.example.espoti.ui.components.PreviousMeetingCard
import com.example.espoti.ui.components.UpcomingMeetingCard
import com.example.espoti.ui.components.VotingMeetingCard
import com.example.espoti.ui.components.formatDateTime
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.TextCream
import com.example.espoti.viewmodel.MeetingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Meetings tabs (Upcoming / Previous / Canceled) from Firestore. Meetings
 * still voting are listed first in Upcoming with a "Voting" chip.
 * [highlightMeetingId] (set after voting) opens Upcoming, scrolls to that
 * meeting and outlines it for a few seconds.
 */
@Composable
fun MeetingsScreen(
    onDetailClick: (Meeting) -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    highlightMeetingId: String? = null,
    viewModel: MeetingsViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val selectedTab by viewModel.selectedTab
    val allMeetings by viewModel.meetings
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val highlightedId by viewModel.highlightedMeetingId

    // Reload every time the screen is shown (a vote may have just changed it).
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(highlightMeetingId) {
        highlightMeetingId?.let(viewModel::highlight)
    }

    val meetings = remember(allMeetings, selectedTab) { viewModel.meetingsFor(selectedTab) }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {

            MeetingsHeader(hasUnreadNotifications = hasUnreadNotifications, onNotificationsClick = onNotificationsClick)
            Spacer(modifier = Modifier.height(24.dp))

            MeetingsTabs(selectedTab=selectedTab,
                        onTabSelected = viewModel::onTabSelected
            )
            Spacer(modifier = Modifier.height(24.dp))

            when {
                isLoading && allMeetings.isEmpty() -> {
                    CircularProgressIndicator(
                        color = BrandBrown,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                error != null && allMeetings.isEmpty() -> {
                    Text(text = error.orEmpty(), color = BrandBrown)
                    Spacer(modifier = Modifier.height(8.dp))
                    EspotiPrimaryButton(text = "Retry", onClick = viewModel::refresh)
                }

                meetings.isEmpty() -> {
                    Text(
                        text = when (selectedTab) {
                            MeetingTab.UPCOMING -> "No upcoming meetings. Create one with +."
                            MeetingTab.PREVIOUS -> "No previous meetings yet."
                            MeetingTab.CANCELED -> "No canceled meetings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandBrown
                    )
                }

                else -> when (selectedTab) {
                    MeetingTab.UPCOMING -> {
                        // Travel status / check-in only make sense for confirmed
                        // meetings with a place and a start time.
                        val located = meetings.filter {
                            !it.isVoting && it.latitude != null && it.startsAtEpochMillis != null
                        }
                        if (located.isNotEmpty()) {
                            MeetingLocationAccess(
                                viewModel = viewModel,
                                meetings = located
                            )
                        }

                        meetings.forEach { meeting ->
                            HighlightableMeeting(
                                highlighted = meeting.id == highlightedId,
                                onHighlightShown = viewModel::clearHighlight
                            ) { modifier ->
                                if (meeting.isVoting) {
                                    VotingMeetingCard(
                                        meeting = meeting,
                                        onVoteClick = onDetailClick,
                                        modifier = modifier
                                    )
                                } else {
                                    UpcomingMeetingCard(
                                        meeting = meeting,
                                        onDetailClick = onDetailClick,
                                        onTakePhotoClick = {},
                                        status = viewModel.contexts[meeting.id],
                                        loading = viewModel.loading,
                                        onCheckIn = {
                                            scope.launch {
                                                viewModel.refreshContext(
                                                    meetings = located,
                                                    checkIn = meeting
                                                )
                                            }
                                        },
                                        modifier = modifier
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    MeetingTab.PREVIOUS -> {
                        meetings.forEach { meeting ->
                            PreviousMeetingCard(
                                meeting = meeting,
                                date = meeting.startsAtEpochMillis?.let(::formatDateTime) ?: "",
                                showMemories = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    MeetingTab.CANCELED -> {
                        meetings.forEach { meeting ->
                            CanceledMeetingCard(
                                meeting = meeting,
                                date = meeting.startsAtEpochMillis?.let(::formatDateTime) ?: "",
                                onAttendClick = {
                                    // Backend :(
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a meeting card: when [highlighted], scrolls it into view and draws an
 * orange outline for a few seconds, then calls [onHighlightShown].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HighlightableMeeting(
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(highlighted) {
        if (highlighted) {
            requester.bringIntoView()
            delay(4_000L)
            onHighlightShown()
        }
    }

    content(
        Modifier
            .bringIntoViewRequester(requester)
            .then(
                if (highlighted) {
                    Modifier.border(3.dp, BrandOrange, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun MeetingsHeader(hasUnreadNotifications: Boolean, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EspotiLogo(size = 55.dp)
        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

@Composable
private fun MeetingsTabs(
    selectedTab: MeetingTab,
    onTabSelected: (MeetingTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MeetingTabButton(
            text ="Upcoming",
            selected = selectedTab == MeetingTab.UPCOMING,
            onClick = {
                onTabSelected(MeetingTab.UPCOMING)
            }
        )
        MeetingTabButton(
            text ="Previous",
            selected = selectedTab == MeetingTab.PREVIOUS,
            onClick = {
                onTabSelected(MeetingTab.PREVIOUS)
            }
        )
        MeetingTabButton(
            text ="Canceled",
            selected = selectedTab == MeetingTab.CANCELED,
            onClick = {
                onTabSelected(MeetingTab.CANCELED)
            }
        )
    }
}

@Composable
private fun MeetingTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(
                if (selected){
                    BrandBrown
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .clickable{
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) {
            TextCream
        } else {
            BrandBrown
        },
        style = MaterialTheme.typography.bodyMedium
    )
}
