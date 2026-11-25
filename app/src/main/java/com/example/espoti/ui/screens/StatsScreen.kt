package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.MonthCount
import com.example.espoti.model.domain.RankedItem
import com.example.espoti.model.domain.TrendingPlace
import com.example.espoti.model.domain.UserStats
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.SavedDataCaption
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.StatsViewModel

// ============================================================================
// MY STATS SCREEN (Sprint 4, opened from Profile)
// ----------------------------------------------------------------------------
// Layout (top -> bottom):
//   1. Header: back, title, refresh, notifications bell
//   2. Saved-data caption / error (eventual connectivity)
//   3. Summary tiles: meetups, upcoming, canceled, votes
//   4. "Your vote decided": % of decided meetings that went with your vote
//   5. Friends you meet most, favorite places, favorite activity
//   6. Meetups per month (last 6 months, simple bars, no chart library)
//   7. Trending on Espoti (analytics_results/popular_meeting_areas)
// All numbers come ready from StatsViewModel; this file only draws them.
// ============================================================================
@Composable
fun StatsScreen(
    onBackClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val stats by viewModel.stats
    val isLoading by viewModel.isLoading
    val notice by viewModel.notice
    val error by viewModel.error
    val isOnline by viewModel.isOnline

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            StatsHeader(
                isLoading = isLoading,
                onBackClick = onBackClick,
                onRefreshClick = { viewModel.refresh(force = true) },
                hasUnreadNotifications = hasUnreadNotifications,
                onNotificationsClick = onNotificationsClick
            )

            notice?.let {
                SavedDataCaption(text = it, modifier = Modifier.padding(top = 8.dp))
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val current = stats
            when {
                current == null && isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = BrandOrange) }

                current != null -> StatsContent(stats = current, isOnline = isOnline)
            }
        }
    }
}

@Composable
private fun StatsHeader(
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    hasUnreadNotifications: Boolean,
    onNotificationsClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandBrown)
        }
        Text(
            text = "My stats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandBrown,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(color = BrandOrange, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            IconButton(onClick = onRefreshClick) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = BrandBrown)
            }
        }
        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

@Composable
private fun StatsContent(stats: UserStats, isOnline: Boolean) {
    Spacer(modifier = Modifier.height(16.dp))

    if (stats.isEmpty) {
        Text(
            text = "No meetups yet. Create one and your stats will show up here.",
            style = MaterialTheme.typography.bodyLarge,
            color = BrandBrown
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryTile("Meetups", stats.attended, Modifier.weight(1f))
            SummaryTile("Upcoming", stats.upcoming, Modifier.weight(1f))
            SummaryTile("Canceled", stats.canceled, Modifier.weight(1f))
            SummaryTile("Votes", stats.votesCast, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        VoteInfluenceCard(stats)

        Spacer(modifier = Modifier.height(16.dp))
        StatsSection(title = "Friends you meet most") {
            RankedList(stats.topFriends, empty = "Finish a meetup with friends to see them here.")
        }

        Spacer(modifier = Modifier.height(12.dp))
        StatsSection(title = "Your favorite places") {
            RankedList(stats.topPlaces, empty = "Your finished meetups' places will show up here.")
        }

        Spacer(modifier = Modifier.height(12.dp))
        StatsSection(title = "Favorite activity") {
            Text(
                text = stats.favoriteActivity?.displayName() ?: "Not enough meetups yet.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (stats.favoriteActivity != null) FontWeight.Bold else FontWeight.Normal,
                color = BrandBrown
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        StatsSection(title = "Meetups per month") { MonthBars(stats.perMonth) }
    }

    Spacer(modifier = Modifier.height(12.dp))
    StatsSection(title = "Trending on Espoti") {
        TrendingList(stats.trendingPlaces, isOnline)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SummaryTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BrandBrown)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = BrandBrown, maxLines = 1)
    }
}

@Composable
private fun VoteInfluenceCard(stats: UserStats) {
    val percent = stats.voteWinPercent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandBrown)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = percent?.let { "$it%" } ?: "–",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandOrange
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Your vote decided",
                style = MaterialTheme.typography.titleMedium,
                color = SurfacePeach,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (percent == null) {
                    "Vote in a meeting to see how often the group goes with your choice."
                } else {
                    "${stats.wonVotes} of ${stats.decidedWithVote} decided meetings where you voted."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SurfacePeach
            )
        }
    }
}

@Composable
private fun StatsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandBrown
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RankedList(items: List<RankedItem>, empty: String) {
    if (items.isEmpty()) {
        Text(text = empty, style = MaterialTheme.typography.bodyMedium, color = BrandBrown)
        return
    }
    items.forEachIndexed { index, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                fontWeight = FontWeight.Bold,
                color = BrandOrange,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = BrandBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = meetupsLabel(item.count),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )
        }
    }
}

/** Six bars scaled to the busiest month. */
@Composable
private fun MonthBars(months: List<MonthCount>) {
    val max = months.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEach { month ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(text = month.count.toString(), style = MaterialTheme.typography.labelMedium, color = BrandBrown)
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .width(22.dp)
                        // A small stub for empty months so the axis is still visible.
                        .fillMaxHeight(fraction = (month.count.toFloat() / max * 0.7f).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (month.count > 0) BrandOrange else BrandBrown.copy(alpha = 0.2f))
                )
                Text(
                    text = month.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandBrown,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TrendingList(places: List<TrendingPlace>, isOnline: Boolean) {
    if (places.isEmpty()) {
        Text(
            text = if (isOnline) "No trending places yet." else "Available when you're online.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )
        return
    }
    Text(
        text = "Where people meet the most on Espoti",
        style = MaterialTheme.typography.bodySmall,
        color = BrandBrown
    )
    Spacer(modifier = Modifier.height(4.dp))
    places.forEachIndexed { index, place ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                fontWeight = FontWeight.Bold,
                color = BrandOrange,
                modifier = Modifier.width(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrandBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (place.cityName.isNotBlank()) {
                    Text(text = place.cityName, style = MaterialTheme.typography.bodySmall, color = BrandBrown)
                }
            }
            Text(text = meetupsLabel(place.selections), style = MaterialTheme.typography.bodyMedium, color = BrandBrown)
        }
    }
}

private fun meetupsLabel(count: Int) = if (count == 1) "1 meetup" else "$count meetups"

private fun ActivityType.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

@Preview(showBackground = true)
@Composable
private fun StatsContentPreview() {
    EspotiTheme {
        Column(modifier = Modifier.padding(20.dp)) {
            StatsContent(
                stats = UserStats(
                    attended = 7, upcoming = 2, canceled = 1, votesCast = 8,
                    decidedWithVote = 6, wonVotes = 4,
                    topFriends = listOf(RankedItem("1", "juan", 5), RankedItem("2", "santi", 3)),
                    topPlaces = listOf(RankedItem("p", "Cafe Central", 3)),
                    favoriteActivity = ActivityType.COFFEE,
                    perMonth = listOf(
                        MonthCount("2026-06", "Jun", 0), MonthCount("2026-07", "Jul", 1),
                        MonthCount("2026-08", "Aug", 2), MonthCount("2026-09", "Sep", 1),
                        MonthCount("2026-10", "Oct", 3), MonthCount("2026-11", "Nov", 0)
                    ),
                    trendingPlaces = listOf(TrendingPlace("Cafe Central", "Bogotá", 12))
                ),
                isOnline = true
            )
        }
    }
}
