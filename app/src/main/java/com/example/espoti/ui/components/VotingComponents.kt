package com.example.espoti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.ui.theme.TextCream
import java.util.Locale

// ============================================================================
// VOTING COMPONENTS
// ----------------------------------------------------------------------------
// Shared by CreateMeeting2 (creator votes) and MeetingDetail (invited friends
// vote): the selectable place card, the bottom Vote bar and friend avatars.
// ============================================================================

/**
 * Selectable place card. [subtitle] is the distance in CreateMeeting2 and the
 * vote count in MeetingDetail.
 */
@Composable
fun RecommendationCard(
    title: String,
    rating: Int,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    SurfacePeach
                } else {
                    Color.White
                }
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFF9800))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                repeat(5) { index ->
                    Text(
                        text = if (index < rating) "★" else "☆",
                        color = BrandBrown,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )
        }

        // Selection indicator
        if (isSelected) {
            Text(
                text = "✓",
                color = BrandBrown,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/**
 * Bottom bar with the Vote button, so it stays visible however long the list
 * is. Vote is enabled only once a place is chosen; until then [hint] explains
 * why. Save errors are shown under the button.
 */
@Composable
fun VoteBar(
    canVote: Boolean,
    showHint: Boolean,
    errorMessage: String?,
    onVote: () -> Unit,
    hint: String = "Select a place to vote",
    buttonText: String = "Vote"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showHint) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        EspotiPrimaryButton(
            text = buttonText,
            onClick = onVote,
            enabled = canVote,
            modifier = Modifier
                .width(130.dp)
                .height(42.dp)
        )

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Circle with the friend's initial; tapping it runs [onClick] (e.g. remove from the invite). */
@Composable
fun FriendAvatar(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfacePeach)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "?",
            color = BrandBrown,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Small orange "Voting" chip shown on meetings whose place is still being voted. */
@Composable
fun VotingChip(modifier: Modifier = Modifier) {
    Text(
        text = "Voting",
        style = MaterialTheme.typography.labelMedium,
        color = TextCream,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(BrandOrange)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
