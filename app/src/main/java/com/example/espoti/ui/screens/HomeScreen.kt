package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.HomeMeetingCard
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.HomeViewModel

// ============================================================================
// INICIO (Home) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left) + "Hello, <name> / Where do we meet?",
//      notifications bell (right)
//   2. "Create new meeting" button (full width, brown pill)
//   3. "Next Meetings" -> horizontal row of the user's VOTING/UPCOMING meetings
//   4. "Invite us a coffee" donation card
//   5. "Meeting History" / "Previous Events" -> row of PREVIOUS meetings
// Data comes from Firestore through HomeViewModel; tapping a card opens its
// MeetingDetail. HomeContent is the stateless part (used by the preview).
// ============================================================================

@Composable
fun HomeScreen(
    onCreateMeetingClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    onMeetingClick: (String) -> Unit = {},
    onSeeAllMeetingsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val userName by viewModel.userName
    val nextMeetings by viewModel.nextMeetings
    val history by viewModel.history
    val hasLoaded by viewModel.hasLoaded
    val error by viewModel.error

    // The ViewModel outlives trips to other tabs, so reload every time Home is
    // shown (picks up meetings created or voted meanwhile).
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeContent(
        userName = userName,
        nextMeetings = nextMeetings,
        history = history,
        hasLoaded = hasLoaded,
        error = error,
        hasUnreadNotifications = hasUnreadNotifications,
        onNotificationsClick = onNotificationsClick,
        onCreateMeetingClick = onCreateMeetingClick,
        onMeetingClick = onMeetingClick,
        onSeeAllMeetingsClick = onSeeAllMeetingsClick
    )
}

@Composable
private fun HomeContent(
    userName: String?,
    nextMeetings: List<HomeMeetingCard>,
    history: List<HomeMeetingCard>,
    hasLoaded: Boolean,
    error: String?,
    hasUnreadNotifications: Boolean,
    onNotificationsClick: () -> Unit,
    onCreateMeetingClick: () -> Unit,
    onMeetingClick: (String) -> Unit,
    onSeeAllMeetingsClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            HomeHeader(
                userName = userName,
                hasUnreadNotifications = hasUnreadNotifications,
                onNotificationsClick = onNotificationsClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            EspotiPrimaryButton(
                text = "Create new meeting",
                onClick = onCreateMeetingClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next Meetings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (nextMeetings.isNotEmpty()) {
                    TextButton(onClick = onSeeAllMeetingsClick) {
                        Text(text = "See all", color = BrandOrange)
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            MeetingsRow(
                meetings = nextMeetings,
                hasLoaded = hasLoaded,
                emptyText = "No meetings yet. Create one and invite your friends!"
            ) { MeetingCard(it, onClick = { onMeetingClick(it.id) }) }

            Spacer(modifier = Modifier.height(20.dp))

            CoffeeCard()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Meeting History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Previous Events",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOrange
            )

            Spacer(modifier = Modifier.height(12.dp))

            MeetingsRow(
                meetings = history,
                hasLoaded = hasLoaded,
                emptyText = "Your finished meetings will show up here."
            ) { HistoryCard(it, onClick = { onMeetingClick(it.id) }) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Horizontal row of cards. Before the first load it shows two empty boxes
 * (so the layout doesn't jump); after it, [emptyText] if there is nothing.
 */
@Composable
private fun MeetingsRow(
    meetings: List<HomeMeetingCard>,
    hasLoaded: Boolean,
    emptyText: String,
    card: @Composable (HomeMeetingCard) -> Unit
) {
    when {
        meetings.isNotEmpty() -> Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            meetings.forEach { card(it) }
        }

        !hasLoaded -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { LoadingPlaceholder() }
        }

        else -> Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun HomeHeader(
    userName: String?,
    hasUnreadNotifications: Boolean,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EspotiLogo(size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (userName != null) "Hello, $userName" else "Hello,",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Where do we meet?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

@Composable
private fun MeetingCard(meeting: HomeMeetingCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        if (meeting.isVoting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.HowToVote,
                    contentDescription = null,
                    tint = BrandOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Voting",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = meeting.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = BrandBrown,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = meeting.whenLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        AvatarStack(count = meeting.avatarCount)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = meeting.peopleLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AvatarStack(count: Int) {
    Row {
        // ICON SPOT: swap each circle for a real profile picture, e.g.
        //   Image(painter = painterResource(R.drawable.avatar1), ...)
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandBrown)
            )
        }
    }
}

@Composable
private fun CoffeeCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Invite us a coffee",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )
            // ICON SPOT: coffee cup icon.
            Text("☕", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Help us grow, make a contribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EspotiPrimaryButton(
                text = "Support",
                onClick = { /* not wired up in this prototype */ },
                modifier = Modifier
                    .width(110.dp)
                    .height(40.dp)
            )
        }
    }
}

/** A finished meeting: place, when it was and who went. */
@Composable
private fun HistoryCard(meeting: HomeMeetingCard, onClick: () -> Unit) {
    // ICON SPOT: once memories exist, show the meeting's first photo here.
    Column(
        modifier = Modifier
            .size(width = 160.dp, height = 110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = BrandOrange,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = BrandBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meeting.whenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = BrandBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meeting.peopleLabel,
                style = MaterialTheme.typography.bodySmall,
                color = BrandBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    EspotiTheme {
        HomeContent(
            userName = "ana",
            nextMeetings = listOf(
                HomeMeetingCard("1", "Coffee meeting", "Voting closes Tomorrow, 9:00 AM", "Juan and 2 more", 3, isVoting = true),
                HomeMeetingCard("2", "Cafe Central", "Today, 4:00 PM", "Juan", 2)
            ),
            history = listOf(HomeMeetingCard("3", "Parque 93", "Oct 20, 6:00 PM", "Juan and 1 more", 3)),
            hasLoaded = true,
            error = null,
            hasUnreadNotifications = false,
            onNotificationsClick = {},
            onCreateMeetingClick = {},
            onMeetingClick = {},
            onSeeAllMeetingsClick = {}
        )
    }
}
