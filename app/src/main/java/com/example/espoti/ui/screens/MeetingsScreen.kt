package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.TextCream
import com.example.espoti.ui.components.UpcomingMeetingCard
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingTab
import com.example.espoti.viewmodel.MeetingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.ui.components.PreviousMeetingCard
import com.example.espoti.ui.components.CanceledMeetingCard

@Composable
fun MeetingsScreen(
    onDetailClick: (Meeting) -> Unit,
    viewModel: MeetingsViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab
    val meetings = viewModel.meetings
    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {

            MeetingsHeader()
            Spacer(modifier = Modifier.height(24.dp))

            MeetingsTabs(selectedTab=selectedTab,
                        onTabSelected = viewModel::onTabSelected
            )
            Spacer(modifier = Modifier.height(24.dp))

            when (selectedTab) {

                MeetingTab.UPCOMING -> {
                    meetings.forEach { meeting ->


                        UpcomingMeetingCard(
                            meeting = meeting,
                            onDetailClick = onDetailClick,
                            onTakePhotoClick = {}
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                MeetingTab.PREVIOUS -> {
                    PreviousMeetingCard(
                        meeting = meetings[0],
                        date = "Marzo 2, 2026",
                        showMemories = true
                    )
                }
                MeetingTab.CANCELED -> {
                    CanceledMeetingCard(
                        meeting = meetings[0],
                        date = "Tomorrow",
                        onAttendClick = {
                            // Backend :(
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingsHeader() {
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
