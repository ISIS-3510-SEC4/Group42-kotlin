package com.example.espoti.ui.screens


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.espoti.R
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.components.FriendAvatar
import com.example.espoti.ui.components.RecommendationCard
import com.example.espoti.ui.components.VoteBar
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.SurfacePeach
import androidx.compose.runtime.getValue
import com.example.espoti.viewmodel.CreateMeetingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.text.DecimalFormat

@Composable
fun CreateMeetingScreen2(
    onVoteClick: (meetingId: String) -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val selectedPlaceId by viewModel.selectedPlaceId
    val friends by viewModel.friends
    val selectedFriendIds by viewModel.selectedFriendIds
    val invitedFriends = friends.filter { it.id in selectedFriendIds }
    val state by viewModel.recommendationState
    val isSavingMeeting by viewModel.isSavingMeeting
    val saveMeetingError by viewModel.saveMeetingError

    // Normally Schedule already started the request.
    // This also handles re-entering after a cancelled screen instance.
    LaunchedEffect(viewModel) {
        viewModel.ensureRecommendations()
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelRecommendations()
        }
    }

    val hasRecommendations =
        !state.isLoading && state.error == null && state.recommendations.isNotEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            VoteBar(
                canVote = hasRecommendations && selectedPlaceId != null && !isSavingMeeting,
                showHint = hasRecommendations && selectedPlaceId == null,
                errorMessage = saveMeetingError,
                // Navigates only after the meeting is saved (see onVoteConfirmed).
                onVote = { viewModel.onVoteConfirmed(onDone = onVoteClick) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        Spacer(modifier = Modifier.height(15.dp))

            CreateMeeting2Header()

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "You will meet",
                style = MaterialTheme.typography.titleLarge,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(22.dp))

            // You + the friends invited in CreateMeeting1.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Avatar()
                invitedFriends.forEach { friend -> FriendAvatar(name = friend.username) }
            }

            if (invitedFriends.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Only you: the place is decided with your vote.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your friends will vote too. The place is decided when everybody votes or the voting closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Our recommendations:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.error != null -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = BrandBrown
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    EspotiPrimaryButton(
                        text = "Retry",
                        onClick = viewModel::retryRecommendations
                    )
                }

                state.recommendations.isEmpty() -> {
                    Text(
                        text = "No recommendations found.",
                        color = BrandBrown
                    )
                }

                else -> {
                    state.recommendations.forEach { recommendation ->
                        val requestId = state.requestId

                        // Mutable holder without Compose state updates
                        // during the drawing pass.
                        val visible = remember(
                            requestId,
                            recommendation.placeId
                        ) {
                            booleanArrayOf(false)
                        }

                        val displayModifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()

                                visible[0] =
                                    bounds.width > 0f &&
                                            bounds.height > 0f
                            }
                            .drawWithContent {
                                drawContent()

                                if (visible[0] && requestId != null) {
                                    viewModel.onRecommendationsDisplayed(
                                        requestId
                                    )
                                    viewModel.onRecommendationViewed(
                                        requestId = requestId,
                                        recommendation = recommendation
                                    )
                                }
                            }

                        val distance = recommendation.distanceKm
                            ?.let { kilometers ->
                                "${DecimalFormat("0.##").format(kilometers)} Km away"
                            }
                            ?: "Distance unavailable"

                        RecommendationCard(
                            title = recommendation.name,
                            rating = recommendation.rating,
                            subtitle = distance,
                            isSelected =
                                selectedPlaceId == recommendation.placeId,
                            onClick = {
                                viewModel.onRestaurantSelected(
                                    recommendation
                                )
                            },
                            modifier = displayModifier
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfacePeach),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.profileicon),
            contentDescription = "Avatar Logo",
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CreateMeeting2Header() {
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
