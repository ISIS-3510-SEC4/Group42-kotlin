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
    onVoteClick: () -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val selectedRestaurant by viewModel.selectedRestaurant
    val state by viewModel.recommendationState

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

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
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

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar()
                Spacer(modifier = Modifier.width(8.dp))
                Avatar()
                Spacer(modifier = Modifier.width(8.dp))
                Avatar()
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
                            distance = distance,
                            isSelected =
                                selectedRestaurant == recommendation.name,
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

            Spacer(modifier = Modifier.height(14.dp))

            EspotiPrimaryButton(
                text = "Vote",
                onClick = {
                    viewModel.onVoteConfirmed()
                    onVoteClick()
                },
                enabled =
                    !state.isLoading &&
                            state.error == null &&
                            state.recommendations.isNotEmpty(),
                modifier = Modifier
                    .width(130.dp)
                    .height(42.dp)
                    .align(Alignment.CenterHorizontally)
            )
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
@Composable
private fun RecommendationCard(
    title: String,
    rating: Int,
    distance: String,
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
                text = distance,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )
        }

        // Indicador de selección
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