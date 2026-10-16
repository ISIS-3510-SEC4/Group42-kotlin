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
import com.example.espoti.ui.components.OpenStreetMapView
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateListOf
import com.example.espoti.model.Friend
import com.example.espoti.viewmodel.FriendsViewModel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SelectableDates
import androidx.compose.material.icons.filled.HowToVote
import java.time.ZoneId


@Composable
fun CreateMeetingScreen1(
    onScheduleClick: () -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val whatToDo by viewModel.whatToDo
    val selectedDate by viewModel.selectedDate
    val selectedTime by viewModel.selectedTime
    val selectedLatitude by viewModel.selectedLatitude
    val selectedLongitude by viewModel.selectedLongitude
    val friendsViewModel: FriendsViewModel = viewModel()
    val friends by friendsViewModel.friends
    val timeError by viewModel.timeError
    val selectedFriendIds by viewModel.selectedFriendIds
    val votingDeadlineMillis by viewModel.votingDeadlineMillis
    val votingDeadlineError by viewModel.votingDeadlineError

    var showFriendsDialog by remember { mutableStateOf(false) }

    // The invite list lives in the ViewModel: it is what the meeting is
    // created with, and it survives going to step 2 and back.
    val selectedFriends = friends.filter { it.userId in selectedFriendIds }
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
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                selectedFriends.forEach { friend ->
                    Avatar(
                        contentDescription = friend.name
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                }

                AddPersonButton(
                    onClick = {
                        showFriendsDialog = true
                    }
                )
            }

            if (selectedFriends.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedFriends.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }


            Spacer(modifier = Modifier.height(18.dp))

            Spacer(modifier = Modifier.height(5.dp))

            EspotiField(
                label = "What are we going to do?",
                value = whatToDo,
                onValueChange = viewModel::onWhatToDoChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "What day?",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            MeetingDatePickerField(
                selectedDate = selectedDate,
                onDateSelected = viewModel::onDateSelected
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "What time?",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            MeetingTimePickerField(
                selectedTime = selectedTime,
                errorMessage = timeError,
                onTimeSelected = viewModel::onTimeSelected
            )

            Spacer(modifier = Modifier.height(12.dp))

            VotingDeadlineField(
                deadlineMillis = votingDeadlineMillis,
                meetingDate = selectedDate,
                errorMessage = votingDeadlineError,
                onDeadlineSelected = viewModel::onVotingDeadlineSelected
            )

            Spacer(modifier = Modifier.height(14.dp))

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
            ) {

                OpenStreetMapView(
                    modifier = Modifier.fillMaxSize(),
                    selectedLatitude = selectedLatitude,
                    selectedLongitude = selectedLongitude,
                    onLocationSelected = viewModel::onLocationSelected
                )

                Text(
                    text = "© OpenStreetMap contributors",
                    fontSize = 9.sp,
                    color = Color.DarkGray,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
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

    if (showFriendsDialog) {
        SelectFriendsDialog(
            friends = friends,
            selectedFriends = selectedFriends,
            onFriendToggle = { friend ->
                viewModel.toggleFriend(friend.userId)
            },
            onDismiss = {
                showFriendsDialog = false
            }
        )
    }

}





@Composable
private fun Avatar(
    contentDescription: String = "Avatar"
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfacePeach),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.profileicon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}




@Composable
private fun AddPersonButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfacePeach)
            .clickable(onClick = onClick),
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

@Composable
private fun MeetingDatePickerField(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val formatter = DateTimeFormatter.ofPattern(
        "MMMM d, yyyy",
        Locale.ENGLISH
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedDate?.format(formatter) ?: "Select a date",
                color = BrandBrown
            )

            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Select date",
                tint = BrandBrown
            )
        }
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
            selectableDates = futureDates()
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()

                            onDateSelected(date)
                        }
                        showDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
private fun MeetingTimePickerField(
    selectedTime: LocalTime?,
    errorMessage: String?,
    onTimeSelected: (LocalTime) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val formatter = DateTimeFormatter.ofPattern(
        "h:mm a",
        Locale.ENGLISH
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedTime?.format(formatter) ?: "Select a time",
                color = BrandBrown
            )

            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Select time",
                tint = BrandBrown
            )
        }
    }

    // The ViewModel rejects a time that already passed on the chosen day.
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime?.hour ?: 12,
            initialMinute = selectedTime?.minute ?: 0,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Select a time")
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(
                            LocalTime.of(
                                timePickerState.hour,
                                timePickerState.minute
                            )
                        )
                        showDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun SelectFriendsDialog(
    friends: List<Friend>,
    selectedFriends: List<Friend>,
    onFriendToggle: (Friend) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Who will go?")
        },
        text = {
            if (friends.isEmpty()) {
                Text("You don't have any friends yet.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    friends.forEach { friend ->
                        val isSelected = selectedFriends.any {
                            it.userId == friend.userId
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFriendToggle(friend)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    onFriendToggle(friend)
                                }
                            )

                            Text(
                                text = friend.name,
                                color = BrandBrown
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/** Today's local date expressed as UTC midnight, like the DatePicker's values. */
private fun todayUtcMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Days from today up to [lastUtcMillis] (inclusive) when given. */
@OptIn(ExperimentalMaterial3Api::class)
private fun futureDates(lastUtcMillis: Long? = null) = object : SelectableDates {
    private val today = todayUtcMillis()

    override fun isSelectableDate(utcTimeMillis: Long) =
        utcTimeMillis >= today && (lastUtcMillis == null || utcTimeMillis <= lastUtcMillis)

    override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
}

/**
 * "Voting closes" field (same look as the date/time fields): a DatePicker
 * (today .. meeting day) followed by a TimePicker. The local day + time
 * become epoch millis in the device zone; the ViewModel validates them
 * (future, not after the meeting) and [errorMessage] shows why a choice
 * was rejected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VotingDeadlineField(
    deadlineMillis: Long?,
    meetingDate: LocalDate?,
    errorMessage: String?,
    onDeadlineSelected: (Long) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    // Day picked in the first dialog; non-null opens the time dialog.
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }

    Text(
        text = "Voting closes",
        style = MaterialTheme.typography.bodyMedium,
        color = BrandBrown
    )

    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
            .clickable { showDatePicker = true }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deadlineMillis?.let(::formatDeadline) ?: "In 24 hours (default)",
                color = BrandBrown
            )

            Icon(
                imageVector = Icons.Default.HowToVote,
                contentDescription = "Select voting deadline",
                tint = BrandBrown
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = errorMessage ?: "Your friends can vote until then, or until everybody has voted.",
        style = MaterialTheme.typography.bodySmall,
        color = if (errorMessage != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            selectableDates = futureDates(
                lastUtcMillis = meetingDate
                    ?.atStartOfDay(ZoneOffset.UTC)
                    ?.toInstant()
                    ?.toEpochMilli()
            )
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDay = dateState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                    enabled = dateState.selectedDateMillis != null
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    pickedDay?.let { day ->
        val timePickerState = rememberTimePickerState(
            initialHour = 20,
            initialMinute = 0,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            title = { Text("Voting closes at") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = day.atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onDeadlineSelected(millis)
                        pickedDay = null
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pickedDay = null }) { Text("Cancel") }
            }
        )
    }
}

/** "Oct 15, 8:00 PM" in the device zone. */
private fun formatDeadline(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH))
