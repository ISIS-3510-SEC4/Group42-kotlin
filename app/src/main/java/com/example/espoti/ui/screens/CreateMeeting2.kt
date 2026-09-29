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

@Composable
fun CreateMeetingScreen2(
    onVoteClick: () -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val selectedRestaurant by viewModel.selectedRestaurant

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "You will meet",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Absolute.Left,
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
                color = BrandBrown,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            RecommendationCard(
                title = "Restaurante 1",
                rating = 4,
                distance = "1 Km away",
                isSelected = selectedRestaurant == "Restaurante 1",
                onClick = {
                    viewModel.onRestaurantSelected("Restaurante 1")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            RecommendationCard(
                title = "Restaurante 2",
                rating = 3,
                distance = "2 Km away",
                isSelected = selectedRestaurant == "Restaurante 2",
                onClick = {
                    viewModel.onRestaurantSelected("Restaurante 2")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            RecommendationCard(
                title = "Restaurante 3",
                rating = 5,
                distance = "1.5 Km away",
                isSelected = selectedRestaurant == "Restaurante 3",
                onClick = {
                    viewModel.onRestaurantSelected("Restaurante 3")
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            EspotiPrimaryButton(
                text = "Vote",
                onClick = onVoteClick,
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