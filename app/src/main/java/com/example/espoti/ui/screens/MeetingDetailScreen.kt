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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.example.espoti.ui.components.MeetingArrivalStatus
import com.example.espoti.ui.components.MeetingLocationAccess
import com.example.espoti.ui.components.distanceLabel
import com.example.espoti.viewmodel.MeetingDetailViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import com.example.espoti.ui.components.RecommendationCard
import com.example.espoti.ui.components.VoteBar
import com.example.espoti.ui.components.VotingChip
import com.example.espoti.ui.components.formatDateTime
import com.example.espoti.ui.components.TransportFiltersPanel
import com.example.espoti.model.TransportFilterCalculator
import java.util.Locale
import com.example.espoti.ui.components.SavedDataCaption
import android.location.Geocoder
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.espoti.ui.components.OpenStreetMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState


/**
 * Detail of one Firestore meeting. While it is VOTING it shows the candidate
 * places with their votes and a fixed Vote bar ([onVoted] gets the meeting id
 * once the vote is saved); once decided it shows the place, travel status and
 * check-in.
 */
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit,
    onVoted: (meetingId: String) -> Unit
) {
    val meeting by viewModel.meeting
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val current = meeting

    when {
        current == null && isLoading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandBrown)
        }

        current == null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = error ?: "Meeting not found.", color = BrandBrown)
            Spacer(modifier = Modifier.height(12.dp))
            EspotiPrimaryButton(
                text = "Back",
                onClick = onBackClick,
                modifier = Modifier
                    .width(130.dp)
                    .height(42.dp)
            )
        }

        current.isVoting -> VotingDetail(
            meeting = current,
            viewModel = viewModel,
            onBackClick = onBackClick,
            onVoted = onVoted
        )

        else -> ConfirmedDetail(
            meeting = current,
            viewModel = viewModel,
            onBackClick = onBackClick
        )
    }
}

/** VOTING: pick one of the candidate places; Vote stays fixed at the bottom. */
@Composable
private fun VotingDetail(
    meeting: Meeting,
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit,
    onVoted: (String) -> Unit
) {
    val allCandidates by viewModel.candidates
    val candidates by viewModel.visibleCandidates
    val filters by viewModel.transportFilters
    val canUseFilters by viewModel.canUseTransportFilters
    val filterError by viewModel.transportFilterError
    val isLoading by viewModel.isLoading
    val voteCounts by viewModel.voteCounts
    val selectedPlaceId by viewModel.selectedPlaceId
    val isSaving by viewModel.isSavingVote
    val voteError by viewModel.voteError
    val voteMessage by viewModel.voteMessage.collectAsState()
    val isOnline by viewModel.isOnline
    val isAddingManualPlace by viewModel.isAddingManualPlace
    val manualPlaceError by viewModel.manualPlaceError
    val manualPlaceMessage by viewModel.manualPlaceMessage


    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var placeName by remember { mutableStateOf("") }
    var addressQuery by remember { mutableStateOf("") }

    var selectedLatitude by remember {
        mutableStateOf<Double?>(null)
    }
    var selectedLongitude by remember {
        mutableStateOf<Double?>(null)
    }

    var selectedAddress by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }

    var searchResults by remember {
        mutableStateOf<List<ManualPlaceSearchResult>>(emptyList())
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var showChooseDialog by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.placeId == selectedPlaceId }?.name
    val isCreator by viewModel.isCreator

    fun searchAddress() {

        if (!isOnline) {
            searchError = "Address search is unavailable offline. Reconnect to search."
            return
        }

        val query = addressQuery.trim()

        if (query.isBlank()) {
            searchError = "Enter an address to search."
            return
        }

        scope.launch {
            isSearching = true
            searchError = null
            searchResults = emptyList()

            try {
                val results = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context, Locale.getDefault())

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 5)

                    addresses.orEmpty().mapNotNull { address ->
                        if (!address.hasLatitude() || !address.hasLongitude()) {
                            null
                        } else {
                            ManualPlaceSearchResult(
                                label = address.getAddressLine(0) ?: query,
                                latitude = address.latitude,
                                longitude = address.longitude,
                                cityName = address.locality
                                    ?: address.subAdminArea
                                    ?: address.adminArea
                                    ?: ""
                            )
                        }
                    }
                }

                searchResults = results

                if (results.isEmpty()) {
                    searchError = "No addresses found."
                }
            } catch (e: Exception) {
                searchError = "Couldn't search this address. Try again."
            } finally {
                isSearching = false
            }
        }
    }

    // Organizer only: confirm before skipping the group voting.
    if (showChooseDialog && selectedName != null) {
        AlertDialog(
            onDismissRequest = { showChooseDialog = false },
            title = { Text("Choose for the group?") },
            text = {
                Text("$selectedName will be the place for everyone. Voting ends now and the other votes won't count.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showChooseDialog = false
                    viewModel.onChooseDirectly(onDone = onVoted)
                }) { Text("Choose", color = BrandOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showChooseDialog = false }) {
                    Text("Cancel", color = BrandBrown)
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCreator) {
                    OutlinedButton(
                        onClick = { showChooseDialog = true },
                        enabled = selectedPlaceId != null && !isSaving && !isLoading && isOnline,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Choose for the group", color = BrandBrown)
                    }
                }
                VoteBar(
                    canVote = selectedPlaceId != null &&
                            !isSaving &&
                            !isLoading,
                    showHint = isOnline &&
                            selectedPlaceId == null &&
                            candidates.isNotEmpty(),
                    errorMessage = voteError,
                    onVote = {
                        viewModel.onVoteConfirmed(onDone = onVoted)
                    },
                    buttonText = when {
                        isSaving -> "Saving..."
                        !isOnline -> "Save vote offline"
                        meeting.hasVoted -> "Update vote"
                        else -> "Vote"
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                Text(
                    text = "✕",
                    color = BrandBrown,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            VotingChip()
            Spacer(modifier = Modifier.height(10.dp))
            MeetingDetailDataNotice(viewModel)
            voteMessage?.let { message ->
                Text(
                    text = message,
                    color = BrandBrown,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            meeting.startsAtEpochMillis?.let { start ->
                Text(text = "Meeting: ${formatDateTime(start)}", color = BrandBrown)
            }
            meeting.votingDeadline?.let { deadline ->
                Text(text = "Voting closes ${formatDateTime(deadline)}", color = BrandBrown)
            }
            Text(
                text = "${meeting.voteCount} of ${meeting.participantCount} voted",
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(18.dp))

            TransportFiltersPanel(
                filters = filters,
                hasStartingLocation = canUseFilters,
                isBusy = isSaving || isLoading,
                totalCount = allCandidates.size,
                visibleCount = candidates.size,
                unknownDistanceCount = allCandidates.count {
                    it.distanceKm == null
                },
                error = filterError,
                onApply = viewModel::applyTransportFilters,
                onClear = viewModel::clearTransportFilters
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Add your own place",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = { Text("Place name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isAddingManualPlace
            )

            OutlinedTextField(
                value = addressQuery,
                onValueChange = { addressQuery = it },
                label = { Text("Search an address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isAddingManualPlace
            )

            Button(
                onClick = {
                    searchAddress()
                },
                enabled = !isSearching && !isAddingManualPlace && isOnline,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSearching) "Searching..." else "Search address")
            }

            searchError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            searchResults.forEach { result ->
                TextButton(
                    onClick = {
                        selectedLatitude = result.latitude
                        selectedLongitude = result.longitude
                        selectedAddress = result.label
                        selectedCity = result.cityName
                        addressQuery = result.label
                        searchResults = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.label,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Or select a location on the map",
                color = BrandBrown,
                style = MaterialTheme.typography.bodyMedium
            )

            OpenStreetMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                selectedLatitude = selectedLatitude,
                selectedLongitude = selectedLongitude,
                onLocationSelected = { latitude, longitude ->
                    selectedLatitude = latitude
                    selectedLongitude = longitude
                    selectedAddress = "Selected location ($latitude, $longitude)"
                    selectedCity = ""

                    // Reverse geocoding: obtain the address from the map coordinates.
                    if(isOnline){
                    scope.launch {
                        try {
                            val address = withContext(Dispatchers.IO) {
                                val geocoder = Geocoder(context, Locale.getDefault())

                                @Suppress("DEPRECATION")
                                geocoder.getFromLocation(latitude, longitude, 1)
                                    ?.firstOrNull()
                            }

                            if (address != null) {
                                selectedAddress =
                                    address.getAddressLine(0) ?: selectedAddress

                                selectedCity = address.locality
                                    ?: address.subAdminArea
                                            ?: address.adminArea
                                            ?: ""

                                addressQuery = selectedAddress
                            }
                        } catch (_: Exception) {
                            // The selected coordinates remain valid if reverse geocoding fails.
                        }
                    }
                    }
                }
            )

            if (selectedLatitude != null && selectedLongitude != null) {
                Text(
                    text = "Selected: $selectedAddress",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandBrown
                )
            }

            manualPlaceError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            manualPlaceMessage?.let {
                Text(
                    text = it,
                    color = BrandBrown
                )
            }

            Button(
                onClick = {
                    val latitude = selectedLatitude ?: return@Button
                    val longitude = selectedLongitude ?: return@Button

                    viewModel.addManualPlace(
                        name = placeName.trim(),
                        latitude = latitude,
                        longitude = longitude,
                        address = selectedAddress,
                        cityName = selectedCity
                    )
                },
                enabled = placeName.isNotBlank() &&
                        selectedLatitude != null &&
                        selectedLongitude != null &&
                        !isAddingManualPlace &&
                        isOnline,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAddingManualPlace) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    viewModel.trackFeatureUsed("MAP_SEARCH")
                } else {
                    Text("Add place")
                }
            }

            LaunchedEffect(manualPlaceMessage) {
                if (manualPlaceMessage != null) {
                    placeName = ""
                    addressQuery = ""
                    selectedLatitude = null
                    selectedLongitude = null
                    selectedAddress = ""
                    selectedCity = ""
                    searchResults = emptyList()
                    searchError = null
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Vote for a place:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(color = BrandBrown)
                }

                allCandidates.isEmpty() -> {
                    Text(
                        text = "No candidate places are available.",
                        color = BrandBrown
                    )
                }

                candidates.isEmpty() -> {
                    Text(
                        text = "No places match these filters. " +
                                "Try wider limits or clear the filters.",
                        color = BrandBrown
                    )
                }
            }

            candidates.forEach { place ->
                val votes = voteCounts[place.placeId] ?: 0
                val distance = place.distanceKm
                val minutes = TransportFilterCalculator.walkingMinutes(distance)

                val transportText =
                    if (distance != null && minutes != null) {
                        String.format(
                            Locale.US,
                            "%.2f km · ~%d min walking",
                            distance,
                            minutes
                        )
                    } else {
                        "Distance unavailable"
                    }

                val votesText = if (votes == 1) "1 vote" else "$votes votes"
                RecommendationCard(
                    title = place.name,
                    rating = place.rating,
                    subtitle = "$transportText · $votesText",
                    isSelected = selectedPlaceId == place.placeId,
                    onClick = {
                        viewModel.trackFeatureUsed("LOCATION_LIST")
                        viewModel.onPlaceSelected(place.placeId)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private data class ManualPlaceSearchResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val cityName: String
)

/** Decided meeting: place, travel status and check-in (the original detail). */
@Composable
private fun ConfirmedDetail(
    meeting: Meeting,
    viewModel: MeetingDetailViewModel,
    onBackClick: () -> Unit
) {
    val status = viewModel.contexts[meeting.id]
    val scope = rememberCoroutineScope()
    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clickable{ onBackClick()}
                .padding(horizontal = 4.dp)
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
            MeetingDetailDataNotice(viewModel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "★".repeat(meeting.rating),
                    color = BrandOrange
                )

                Text(
                    text = status?.let {
                        "~${it.walkingMinutes} min walking"
                    } ?: "Location needed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = status?.distanceLabel() ?: "Location needed",
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

            if (meeting.latitude != null && meeting.startsAtEpochMillis != null) {
                MeetingLocationAccess(
                    viewModel = viewModel,
                    meetings = listOf(meeting)
                )
            }

            meeting.startsAtEpochMillis?.let { start ->
                Text(text = "Meeting: ${formatDateTime(start)}")
            }

            MeetingArrivalStatus(
                status = status,
                loading = viewModel.loading,
                onCheckIn = {
                    scope.launch {
                        viewModel.refreshContext(
                            meetings = listOf(meeting),
                            checkIn = meeting
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Travel time",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Text(
                text = status?.let {
                    "~${it.walkingMinutes} minutes walking"
                } ?: "Not available",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "${meeting.participantCount} going:",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(meeting.participantCount.coerceAtMost(5)) {
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
/** Explains whether the detail is showing saved data or a refresh failed. */
@Composable
private fun MeetingDetailDataNotice(
    viewModel: MeetingDetailViewModel
) {
    val isOnline by viewModel.isOnline
    val savedDataNotice by viewModel.savedDataNotice
    val error by viewModel.error
    val isLoading by viewModel.isLoading

    val message = error
        ?: savedDataNotice
        ?: if (!isOnline) {
            "You're offline. Reconnect to refresh this meeting and confirm a vote."
        } else {
            null
        }

    message?.let {
        SavedDataCaption(
            text = it,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isOnline && !isLoading) {
            androidx.compose.material3.TextButton(
                onClick = viewModel::load
            ) {
                Text("Refresh meeting")
            }
        }
    }
}