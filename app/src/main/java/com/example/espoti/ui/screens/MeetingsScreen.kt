package com.example.espoti.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.espoti.ui.components.MeetingLocationAutoRefresh
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
import com.example.espoti.ui.components.SavedDataCaption

/**
 * Meetings tabs (Upcoming / Previous / Canceled) from Firestore. Meetings
 * still voting are listed first in Upcoming with a "Voting" chip.
 * [highlightMeetingId] (set after voting) opens Upcoming, scrolls to that
 * meeting and outlines it for a few seconds.
 *
 * Sprint 4 micro-optimization: a LazyColumn with stable keys (only the cards
 * on screen are composed, and a state change recomposes only those), tabs
 * grouped once per refresh in the ViewModel, `located` and the check-in
 * callback remembered, and state read inside each item instead of the screen.
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
    val isOnline by viewModel.isOnline
    val isShowingSaved by viewModel.isShowingSaved
    val listState = rememberLazyListState()

    // Reload every time the screen is shown (a vote may have just changed it).
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(highlightMeetingId) {
        highlightMeetingId?.let(viewModel::highlight)
    }

    // Already grouped by the ViewModel: just a lookup.
    val meetings = viewModel.meetingsFor(selectedTab)
    val showingSaved = !isOnline || isShowingSaved
    val showRefreshError = error != null && allMeetings.isNotEmpty()

    val body = when {
        showingSaved && selectedTab == MeetingTab.UPCOMING -> MeetingsBody.OFFLINE_UPCOMING
        isLoading && allMeetings.isEmpty() -> MeetingsBody.LOADING
        error != null && allMeetings.isEmpty() -> MeetingsBody.ERROR
        meetings.isEmpty() -> MeetingsBody.EMPTY
        else -> MeetingsBody.LIST
    }

    // Travel status / check-in only make sense for confirmed meetings with a
    // place and a start time. Same list instance until the meetings change.
    val located = remember(meetings) {
        meetings.filter { !it.isVoting && it.latitude != null && it.startsAtEpochMillis != null }
    }
    val showLocation = body == MeetingsBody.LIST && selectedTab == MeetingTab.UPCOMING && located.isNotEmpty()
    if (showLocation) {
        // At screen level, so scrolling the location item away doesn't stop it.
        MeetingLocationAutoRefresh(viewModel = viewModel, meetings = located)
    }
    val onCheckIn: (Meeting) -> Unit = remember(located) {
        { meeting -> scope.launch { viewModel.refreshContext(meetings = located, checkIn = meeting) } }
    }

    // Scroll to the highlighted meeting (it may not be composed yet in a lazy list).
    val itemsBeforeMeetings = 2 + (if (showingSaved) 1 else 0) +
        (if (showRefreshError) 1 else 0) + (if (showLocation) 1 else 0)
    val highlightIndex = remember(meetings, highlightedId, body) {
        if (body == MeetingsBody.LIST) meetings.indexOfFirst { it.id == highlightedId } else -1
    }
    LaunchedEffect(highlightIndex) {
        if (highlightIndex >= 0) listState.animateScrollToItem(itemsBeforeMeetings + highlightIndex)
    }

    Scaffold { innerPadding ->

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {

            item(key = "header", contentType = "header") {
                Column {
                    MeetingsHeader(hasUnreadNotifications = hasUnreadNotifications, onNotificationsClick = onNotificationsClick)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item(key = "tabs", contentType = "tabs") {
                Column {
                    MeetingsTabs(
                        selectedTab = selectedTab,
                        onTabSelected = viewModel::onTabSelected
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (showingSaved) {
                item(key = "saved", contentType = "saved") {
                    SavedDataCaption(
                        text = "Showing meetings available on this device. " +
                                "You can browse Previous and Canceled. " +
                                "Connect to refresh the information.",
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // Keep refresh errors visible even when saved cards are available.
            if (showRefreshError) {
                item(key = "refresh-error", contentType = "error") {
                    Column {
                        Text(
                            text = error.orEmpty(),
                            color = BrandBrown
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EspotiPrimaryButton(
                            text = "Retry",
                            onClick = viewModel::refresh
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            when (body) {
                MeetingsBody.OFFLINE_UPCOMING -> item(key = "status", contentType = "status") {
                    Text(
                        text = "Connect to load Upcoming. " +
                                "You can browse saved meetings in Previous and Canceled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandBrown
                    )
                }

                MeetingsBody.LOADING -> item(key = "status", contentType = "status") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(
                            color = BrandBrown,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                MeetingsBody.ERROR -> item(key = "status", contentType = "status") {
                    Column {
                        Text(text = error.orEmpty(), color = BrandBrown)
                        Spacer(modifier = Modifier.height(8.dp))
                        EspotiPrimaryButton(text = "Retry", onClick = viewModel::refresh)
                    }
                }

                MeetingsBody.EMPTY -> item(key = "status", contentType = "status") {
                    Text(
                        text = if (showingSaved) {
                            when (selectedTab) {
                                MeetingTab.PREVIOUS ->
                                    "No previous meetings are available on this device. Connect to refresh."

                                MeetingTab.CANCELED ->
                                    "No canceled meetings are available on this device. Connect to refresh."

                                MeetingTab.UPCOMING ->
                                    "Connect to load upcoming meetings."
                            }
                        } else {
                            when (selectedTab) {
                                MeetingTab.UPCOMING ->
                                    "No upcoming meetings. Create one with +."

                                MeetingTab.PREVIOUS ->
                                    "No previous meetings yet."

                                MeetingTab.CANCELED ->
                                    "No canceled meetings."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandBrown
                    )
                }

                MeetingsBody.LIST -> when (selectedTab) {
                    MeetingTab.UPCOMING -> {
                        if (showLocation) {
                            item(key = "location", contentType = "location") {
                                MeetingLocationAccess(
                                    viewModel = viewModel,
                                    meetings = located,
                                    autoRefresh = false
                                )
                            }
                        }

                        items(
                            items = meetings,
                            key = { it.id },
                            contentType = { if (it.isVoting) "voting" else "upcoming" }
                        ) { meeting ->
                            Column {
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
                                            // Read here (item scope): a new status or the
                                            // loading flag recomposes only the cards on screen.
                                            status = viewModel.contexts[meeting.id],
                                            loading = viewModel.loading,
                                            onCheckIn = { onCheckIn(meeting) },
                                            modifier = modifier
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    MeetingTab.PREVIOUS -> items(
                        items = meetings,
                        key = { it.id },
                        contentType = { "previous" }
                    ) { meeting ->
                        Column {
                            PreviousMeetingCard(
                                meeting = meeting,
                                date = meetingDateLabel(meeting.startsAtEpochMillis),
                                showMemories = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    MeetingTab.CANCELED -> items(
                        items = meetings,
                        key = { it.id },
                        contentType = { "canceled" }
                    ) { meeting ->
                        Column {
                            CanceledMeetingCard(
                                meeting = meeting,
                                date = meetingDateLabel(meeting.startsAtEpochMillis),
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

/** What the screen shows under the tabs. */
private enum class MeetingsBody { OFFLINE_UPCOMING, LOADING, ERROR, EMPTY, LIST }

/** Card date, formatted once per value instead of on every recomposition. */
@Composable
private fun meetingDateLabel(startsAt: Long?): String =
    remember(startsAt) { startsAt?.let(::formatDateTime) ?: "" }

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
