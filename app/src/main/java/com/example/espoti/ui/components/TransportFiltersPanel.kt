package com.example.espoti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.espoti.model.TransportFilters

@Composable
fun TransportFiltersPanel(
    filters: TransportFilters,
    hasStartingLocation: Boolean,
    isBusy: Boolean,
    totalCount: Int,
    visibleCount: Int,
    unknownDistanceCount: Int,
    error: String?,
    onApply: (TransportFilters) -> Unit,
    onClear: () -> Unit
) {
    var draftDistance by remember(filters) {
        mutableStateOf(filters.maxDistanceKm)
    }

    var draftMinutes by remember(filters) {
        mutableStateOf(filters.maxWalkingMinutes)
    }

    val distanceOptions: List<Pair<Double?, String>> = listOf(
        null to "Any distance",
        1.0 to "Up to 1 km",
        3.0 to "Up to 3 km",
        5.0 to "Up to 5 km",
        10.0 to "Up to 10 km"
    )

    val walkingOptions: List<Pair<Int?, String>> = listOf(
        null to "Any walking time",
        10 to "Up to 10 min",
        20 to "Up to 20 min",
        30 to "Up to 30 min",
        60 to "Up to 60 min"
    )

    val draft = TransportFilters(
        maxDistanceKm = draftDistance,
        maxWalkingMinutes = draftMinutes
    )

    val enabled = hasStartingLocation && !isBusy

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Transport filters",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "From your saved starting location.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!hasStartingLocation) {
            Text(
                text = "Your starting location is needed to use these filters.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        TransportFilterDropdown(
            label = "Maximum distance",
            value = draftDistance,
            options = distanceOptions,
            enabled = enabled,
            onSelected = { draftDistance = it }
        )

        TransportFilterDropdown(
            label = "Estimated walking time",
            value = draftMinutes,
            options = walkingOptions,
            enabled = enabled,
            onSelected = { draftMinutes = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onApply(draft) },
                enabled = enabled && draft != filters,
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply filters")
            }

            OutlinedButton(
                onClick = {
                    draftDistance = null
                    draftMinutes = null
                    onClear()
                },
                enabled = !isBusy && (draft.isActive || filters.isActive),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
        }

        Text(
            text = "$visibleCount of $totalCount places",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Distances are straight-line estimates. " +
                    "Walking times are approximate.",
            style = MaterialTheme.typography.bodySmall
        )

        if (filters.isActive && unknownDistanceCount > 0) {
            Text(
                text = "$unknownDistanceCount places have no distance " +
                        "and are excluded while filters are active.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun <T> TransportFilterDropdown(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second
                    ?: "Custom"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    enabled = enabled,
                    onClick = {
                        onSelected(option.first)
                        expanded = false
                    }
                )
            }
        }
    }
}