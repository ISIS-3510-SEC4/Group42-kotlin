package com.example.espoti.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun OpenStreetMapView(
    modifier: Modifier = Modifier,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    onLocationSelected: (Double, Double) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->

            Configuration.getInstance().userAgentValue =
                "Espoti/1.0 (Android; contact: s.alvarez1123@uniandes.edu.co)"

            MapView(context).apply {

                setMultiTouchControls(true)

                setTileSource(
                    org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
                )

                controller.setZoom(15.0)

                controller.setCenter(
                    GeoPoint(
                        4.6015,
                        -74.0662
                    )
                )

                val mapEventsReceiver = object : MapEventsReceiver {

                    override fun singleTapConfirmedHelper(
                        p: GeoPoint?
                    ): Boolean {

                        p?.let {
                            onLocationSelected(
                                it.latitude,
                                it.longitude
                            )
                        }

                        return true
                    }

                    override fun longPressHelper(
                        p: GeoPoint?
                    ): Boolean {
                        return false
                    }
                }

                overlays.add(
                    MapEventsOverlay(mapEventsReceiver)
                )
            }
        },
        update = { mapView ->

            mapView.overlays.removeAll {
                it is Marker
            }

            if (
                selectedLatitude != null &&
                selectedLongitude != null
            ) {
                val point = GeoPoint(
                    selectedLatitude,
                    selectedLongitude
                )

                val marker = Marker(mapView).apply {
                    position = point

                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )

                    title = "Meeting location"
                }

                mapView.overlays.add(marker)

                mapView.controller.animateTo(point)
            }

            mapView.invalidate()
        }
    )
}