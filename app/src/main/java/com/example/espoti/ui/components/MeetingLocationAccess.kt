package com.example.espoti.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingContext
import com.example.espoti.viewmodel.MeetingContextViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MeetingLocationAccess(
    viewModel: MeetingContextViewModel,
    meetings: List<Meeting>
) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            viewModel.refreshContext(meetings)
        }
    }

    LaunchedEffect(owner, viewModel, meetings) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshContext(meetings)
                delay(30_000L)
            }
        }
    }

    Text(
        if (viewModel.loading) {
            "Updating location…"
        } else {
            viewModel.locationMessage
        }
    )

    TextButton(
        enabled = !viewModel.loading,
        onClick = {
            permissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    ) {
        Text("Enable / refresh location")
    }

    TextButton(
        onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    ) {
        Text("Location permission settings")
    }
}

fun MeetingContext.distanceLabel(): String =
    String.format(
        Locale.US,
        "%.0f m from meeting (straight line)",
        distanceMeters
    )

@Composable
fun MeetingArrivalStatus(
    status: MeetingContext?,
    loading: Boolean,
    onCheckIn: () -> Unit
) {
    Text(
        status?.message ?: "Location needed for arrival detection"
    )

    EspotiPrimaryButton(
        text = if (status?.checkedIn == true) {
            "Arrived"
        } else {
            "Check in"
        },
        enabled = !loading &&
                status?.near == true && !status.checkedIn,
        onClick = onCheckIn
    )
}