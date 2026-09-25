package com.example.espoti.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.espoti.viewmodel.CreateMeetingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espoti.R
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold

@Composable
fun CreateMeetingScreen1(
    onScheduleClick: () -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val whatToDo by viewModel.whatToDo
    val whatDay by viewModel.whatDay
    val whatTime by viewModel.whatTime

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

            CreateMeetin1Header()

            Spacer(modifier = Modifier.height(15.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create a meeting",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Who will go?",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Absolute.Left,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar()
                Spacer(modifier = Modifier.width(8.dp))
                Avatar()
                Spacer(modifier = Modifier.width(8.dp))
                AddPersonButton()
            }

            Spacer(modifier = Modifier.height(18.dp))

            Spacer(modifier = Modifier.height(5.dp))

            EspotiField(
                label = "What are we going to do?",
                value = whatToDo,
                onValueChange = viewModel::onWhatToDoChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            EspotiField(
                label = "What day?",
                value = whatDay,
                onValueChange = viewModel::onWhatDayChange
            )

            Spacer(modifier = Modifier.height(5.dp))

            Spacer(modifier = Modifier.height(12.dp))

            EspotiField(
                label = "What time?",
                value = whatTime,
                onValueChange = viewModel::onWhatTimeChange
            )

            Spacer(modifier = Modifier.height(5.dp))

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select your location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "⌕",
                    fontSize = 22.sp,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Placeholder for the map from the Figma design.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Map",
                    color = BrandBrown,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Simple location pin placeholder
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(BrandOrange)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            EspotiPrimaryButton(
                text = "Schedule",
                onClick = onScheduleClick,
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
private fun AddPersonButton() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfacePeach),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = BrandBrown,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun CreateMeetin1Header() {
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