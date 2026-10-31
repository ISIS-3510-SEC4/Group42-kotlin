
package com.example.espoti.ui.screens

import android.widget.Toast
import android.location.Geocoder
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.domain.Location
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.OpenStreetMapView
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.viewmodel.CreateMeetingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.espoti.di.AppContainer

private data class AddressSearchResult(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun StartingLocationScreen(
    meetingId: String,
    onLocationsCollected: () -> Unit,
    viewModel: CreateMeetingViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isSaving by viewModel.isSavingStartingLocation
    val error by viewModel.startingLocationError
    val previousLocation by viewModel.previousStartingLocation
    val isLoadingPrevious by viewModel.isLoadingPreviousLocation
    val isPreparingCandidates by viewModel.isPreparingCandidates
    val candidatePreparationError by viewModel.candidatePreparationError
    val candidatesReady by viewModel.candidatesReady

    var selectedLatitude by remember { mutableStateOf<Double?>(null) }
    var selectedLongitude by remember { mutableStateOf<Double?>(null) }

    var addressQuery by remember { mutableStateOf("") }
    var searchResults by remember {
        mutableStateOf<List<AddressSearchResult>>(emptyList())
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(meetingId) {
        viewModel.loadPreviousStartingLocation(meetingId)
    }

    LaunchedEffect(candidatesReady) {
        if (candidatesReady) {
            Toast.makeText(
                context,
                "Your location was saved! The meeting is ready to vote.",
                Toast.LENGTH_LONG
            ).show()

            onLocationsCollected()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            CreateStartingLocationHeader()

            Text(
                text = "Your starting location",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Where will you be traveling from?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Search for an address or select a point on the map. " +
                        "Once everyone who accepted has submitted a location, " +
                        "we can find a convenient meeting area.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Address search
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Search for an address",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = {
                        addressQuery = it
                        searchResults = emptyList()
                        searchError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("Address or place") },
                    placeholder = {
                        Text("E.g. a street, university or park")
                    },
                    enabled = !isSearching
                )

                Button(
                    onClick = {
                        val query = addressQuery.trim()

                        if (!AppContainer.connectivity.isOnline.value) {
                            searchError =
                                "Address search is unavailable offline. Please connect to the internet and try again."
                            return@Button
                        }

                        if (query.isBlank()) {
                            searchError = "Enter an address to search."
                            return@Button
                        }

                        scope.launch {
                            isSearching = true
                            searchError = null
                            searchResults = emptyList()

                            try {
                                val results = withContext(Dispatchers.IO) {
                                    val geocoder = Geocoder(
                                        context,
                                        Locale.getDefault()
                                    )

                                    @Suppress("DEPRECATION")
                                    val addresses =
                                        geocoder.getFromLocationName(query, 5)

                                    addresses.orEmpty().mapNotNull { address ->
                                        if (!address.hasLatitude() ||
                                            !address.hasLongitude()
                                        ) {
                                            null
                                        } else {
                                            AddressSearchResult(
                                                label = address.getAddressLine(0)
                                                    ?: address.featureName
                                                    ?: query,
                                                latitude = address.latitude,
                                                longitude = address.longitude
                                            )
                                        }
                                    }
                                }

                                searchResults = results

                                if (results.isEmpty()) {
                                    searchError =
                                        "No results found. Try a more specific address."
                                }
                            } catch (exception: Exception) {
                                searchError =
                                    "Couldn't search for that address. " +
                                            "Try again or select a point on the map."
                            } finally {
                                isSearching = false
                            }
                        }
                    },
                    enabled = !isSearching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Search address")
                    }
                }

                searchError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                searchResults.forEach { result ->
                    OutlinedButton(
                        onClick = {
                            selectedLatitude = result.latitude
                            selectedLongitude = result.longitude
                            addressQuery = result.label
                            searchResults = emptyList()
                            searchError = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.label,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Previous starting location
            if (isLoadingPrevious) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange
                )
            }

            if (previousLocation != null) {
                OutlinedButton(
                    onClick = {
                        selectedLatitude = previousLocation!!.latitude
                        selectedLongitude = previousLocation!!.longitude
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use my previous starting point")
                }
            }

            // Map inside a fixed, clipped container
            Text(
                text = "Or select a point on the map",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                OpenStreetMapView(
                    modifier = Modifier.fillMaxSize(),
                    selectedLatitude = selectedLatitude,
                    selectedLongitude = selectedLongitude,
                    onLocationSelected = { latitude, longitude ->
                        selectedLatitude = latitude
                        selectedLongitude = longitude
                    }
                )
            }

            if (selectedLatitude != null && selectedLongitude != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Selected location",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandBrown
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                selectedLatitude,
                                selectedLongitude
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isPreparingCandidates) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange
                )

                Text(
                    text = "Finding places within 5 km of your meeting center...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (candidatePreparationError != null) {
                Text(
                    text = candidatePreparationError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        viewModel.retryCandidatePreparation(meetingId)
                    },
                    enabled = !isPreparingCandidates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry finding places")
                }
            }

            Button(
                onClick = {
                    val latitude = selectedLatitude
                    val longitude = selectedLongitude

                    if (latitude != null && longitude != null) {
                        viewModel.submitStartingLocation(
                            meetingId = meetingId,
                            latitude = latitude,
                            longitude = longitude,
                            onAllLocationsCollected = { allCollected ->
                                if (!allCollected) {
                                    Toast.makeText(
                                        context,
                                        "Your location was saved successfully! We'll notify you when the meeting is ready.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    onLocationsCollected()
                                }
                            }
                        )
                    }
                },
                enabled = !isSaving &&
                        selectedLatitude != null &&
                        selectedLongitude != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save starting location")
                }
            }
        }
    }
}

@Composable
private fun CreateStartingLocationHeader() {
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
