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
import androidx.compose.material3.MaterialTheme
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

@Composable
fun MeetingDetailScreen(
    meeting: Meeting,
    onBackClick: () -> Unit
) {
    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "★".repeat(meeting.rating),
                    color = BrandOrange
                )

                Text(
                    text = meeting.travelTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = meeting.distance,
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

            Text(
                text = "Travel time",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Text(
                text = "10 minutes",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "They are also going:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
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