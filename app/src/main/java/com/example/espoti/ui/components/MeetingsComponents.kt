package com.example.espoti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.espoti.model.Meeting
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach


@Composable
fun UpcomingMeetingCard(
    meeting: Meeting,
    onDetailClick: (Meeting) -> Unit,
    onTakePhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Text(
            text = ("📍"+meeting.name),
            style = MaterialTheme.typography.titleMedium,
            color = BrandBrown
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "⭐".repeat(meeting.rating),
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
        Spacer(modifier = Modifier.height(18.dp))

        ParticipantAvatars()

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EspotiPrimaryButton(
                text = "Detail",
                onClick = {onDetailClick(meeting)},
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            )
            EspotiPrimaryButton(
                text = "Take a photo",
                onClick = onTakePhotoClick,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                containerColor = BrandOrange
            )
        }
    }
}
@Composable
fun PreviousMeetingCard(
    meeting: Meeting,
    date: String,
    showMemories: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandBrown)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = meeting.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBrown
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )

                Spacer(modifier = Modifier.height(12.dp))

                ParticipantAvatars()
            }

            Text(
                text = "◉",
                color = BrandBrown,
                fontWeight = FontWeight.Bold
            )
        }

        if (showMemories) {

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Memories",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBrown)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(BrandOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CanceledMeetingCard(
    meeting: Meeting,
    date: String,
    onAttendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfacePeach)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BrandBrown)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = meeting.name,
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(10.dp))

            ParticipantAvatars()

            Spacer(modifier = Modifier.height(12.dp))

            EspotiPrimaryButton(
                text = "Attend",
                onClick = onAttendClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                containerColor = BrandOrange
            )
        }

        Text(
            text = "◉",
            color = BrandBrown
        )
    }
}

@Composable
private fun ParticipantAvatars() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BrandBrown)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = "+2",
            color = BrandBrown,
            fontWeight = FontWeight.Bold
        )
    }
}