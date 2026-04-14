package com.vigilex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.vigilex.ui.theme.NavyMid
import kotlinx.coroutines.delay

data class PlaceResult(
    val name:    String,
    val address: String,
    val latLng:  LatLng
)

/**
 * A text field that queries Google Places Autocomplete as the user types
 * and lets them pick a result. Suggestions appear in a scrollable list
 * below the field without dismissing the keyboard.
 *
 * Requires [Places.initialize] to have been called (done in VigileXApplication).
 */
@Composable
fun PlacesAutocompleteField(
    label:            String = "Search destination",
    modifier:         Modifier = Modifier,
    onPlaceSelected:  (PlaceResult) -> Unit
) {
    val context      = LocalContext.current
    val placesClient = remember { Places.createClient(context) }

    var query           by remember { mutableStateOf("") }
    var predictions     by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching     by remember { mutableStateOf(false) }
    var selectedDisplay by remember { mutableStateOf("") }

    // Debounced search — fires 400 ms after the user stops typing
    LaunchedEffect(query) {
        if (selectedDisplay.isNotEmpty() && query == selectedDisplay) return@LaunchedEffect
        selectedDisplay = ""

        if (query.length < 2) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        delay(400L)
        isSearching = true
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("IN")
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                predictions = response.autocompletePredictions
                isSearching = false
            }
            .addOnFailureListener {
                predictions = emptyList()
                isSearching = false
            }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value         = query,
            onValueChange = { query = it },
            label         = { Text(label) },
            singleLine    = true,
            trailingIcon  = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier    = Modifier.fillMaxWidth(0.08f),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Scrollable suggestion list — stays inline, doesn't steal focus or dismiss keyboard
        if (predictions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(top = 4.dp)
            ) {
                items(predictions) { prediction ->
                    val primary   = prediction.getPrimaryText(null).toString()
                    val secondary = prediction.getSecondaryText(null).toString()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDisplay = primary
                                query           = primary
                                predictions     = emptyList()

                                val fields  = listOf(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
                                val req = FetchPlaceRequest.newInstance(prediction.placeId, fields)
                                placesClient.fetchPlace(req)
                                    .addOnSuccessListener { response ->
                                        val place  = response.place
                                        val latLng = place.latLng ?: return@addOnSuccessListener
                                        val name   = place.name ?: primary
                                        selectedDisplay = name
                                        query = name
                                        onPlaceSelected(PlaceResult(name, place.address ?: secondary, latLng))
                                    }
                                    .addOnFailureListener {
                                        selectedDisplay = primary
                                        onPlaceSelected(PlaceResult(primary, secondary, LatLng(0.0, 0.0)))
                                    }
                            },
                        color = NavyMid
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(primary,   color = Color.White,            style = MaterialTheme.typography.bodyMedium)
                            Text(secondary, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(0.1f))
                }
            }
        }
    }
}
