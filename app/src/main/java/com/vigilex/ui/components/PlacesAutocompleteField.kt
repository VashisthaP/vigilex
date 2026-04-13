package com.vigilex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlaceResult(
    val name:    String,
    val address: String,
    val latLng:  LatLng
)

/**
 * A text field that queries Google Places Autocomplete as the user types
 * and lets them pick a result. Calls [onPlaceSelected] with the resolved
 * place name, address, and lat/lng once a suggestion is tapped.
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
    val scope        = rememberCoroutineScope()

    var query           by remember { mutableStateOf("") }
    var predictions     by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var showDropdown    by remember { mutableStateOf(false) }
    var isSearching     by remember { mutableStateOf(false) }
    var selectedDisplay by remember { mutableStateOf("") }   // locked display after selection

    // Debounced search — fires 400 ms after the user stops typing
    LaunchedEffect(query) {
        if (selectedDisplay.isNotEmpty() && query == selectedDisplay) return@LaunchedEffect
        selectedDisplay = ""

        if (query.length < 2) {
            predictions  = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }
        delay(400L)
        isSearching = true
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("IN")          // restrict to India; remove for global
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                predictions  = response.autocompletePredictions
                showDropdown = predictions.isNotEmpty()
                isSearching  = false
            }
            .addOnFailureListener {
                predictions  = emptyList()
                showDropdown = false
                isSearching  = false
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
                        modifier = Modifier
                            .then(Modifier.fillMaxWidth(0.08f)),
                        strokeWidth = androidx.compose.ui.unit.Dp(2f)
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded         = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier         = Modifier.fillMaxWidth(0.9f)
        ) {
            predictions.forEach { prediction ->
                val primary   = prediction.getPrimaryText(null).toString()
                val secondary = prediction.getSecondaryText(null).toString()

                DropdownMenuItem(
                    text = {
                        Column {
                            Text(primary,   color = Color.White,            style = MaterialTheme.typography.bodyMedium)
                            Text(secondary, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        showDropdown    = false
                        selectedDisplay = primary  // block new searches BEFORE changing query
                        query           = primary  // show primary text in field while fetching

                        // Fetch full place details for lat/lng
                        val fields  = listOf(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
                        val request = FetchPlaceRequest.newInstance(prediction.placeId, fields)
                        placesClient.fetchPlace(request)
                            .addOnSuccessListener { response ->
                                val place   = response.place
                                val latLng  = place.latLng ?: return@addOnSuccessListener
                                val name    = place.name   ?: primary
                                selectedDisplay = name
                                query = name
                                onPlaceSelected(PlaceResult(name, place.address ?: secondary, latLng))
                            }
                            .addOnFailureListener {
                                // Fallback: use primary text, lat/lng = 0 (user sees destination name at least)
                                selectedDisplay = primary
                                onPlaceSelected(PlaceResult(primary, secondary, LatLng(0.0, 0.0)))
                            }
                    }
                )
            }
        }
    }
}
