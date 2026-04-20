package com.univ.routeplanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.univ.routeplanner.databinding.ActivityMainBinding
import com.univ.routeplanner.ui.MainViewModel
import com.univ.routeplanner.ui.RouteUiState
import com.univ.routeplanner.util.LocationHelper
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationHelper: LocationHelper

    private var currentLocationMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocation()
        else binding.tvStatus.text = "Location permission denied."
    }

    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val origin = result.data?.getStringExtra(HistoryActivity.EXTRA_ORIGIN)
            val destination = result.data?.getStringExtra(HistoryActivity.EXTRA_DESTINATION)
            if (!origin.isNullOrBlank() && !destination.isNullOrBlank()) {
                viewModel.setCurrentLocation(origin)
                binding.etDestination.setText(destination)
                // If history returns coords, update marker. If it returns names, search.
                handleDestinationInput(destination)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationHelper = LocationHelper(this)

        setupMap()
        setupObservers()
        setupClickListeners()
        viewModel.loadLastCachedRoute()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(33.8938, 35.5018)) // Default to Beirut center
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    private fun setupClickListeners() {
        binding.btnGetLocation.setOnClickListener {
            if (locationHelper.hasLocationPermission()) fetchLocation()
            else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        binding.btnGetRoute.setOnClickListener {
            val input = binding.etDestination.text?.toString()?.trim().orEmpty()
            if (input.isNotBlank()) {
                handleDestinationInput(input)
            } else {
                Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearCache.setOnClickListener {
            viewModel.clearCache()
            currentLocationMarker?.let { binding.mapView.overlays.remove(it) }
            destinationMarker?.let { binding.mapView.overlays.remove(it) }
            routePolyline?.let { binding.mapView.overlays.remove(it) }
            currentLocationMarker = null
            destinationMarker = null
            routePolyline = null
            binding.mapView.invalidate()
            binding.tvStatus.text = "Cache cleared"
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            historyLauncher.launch(intent)
        }
    }

    /**
     * Decides whether to treat input as "Lat,Lng" or search as a "Name"
     */
    private fun handleDestinationInput(input: String) {
        val isCoords = input.contains(",") && input.split(",").all { it.trim().toDoubleOrNull() != null }

        if (isCoords) {
            updateDestinationMarker(input)
            viewModel.fetchRoute(input)
        } else {
            binding.tvStatus.text = "Searching for '$input'..."
            viewModel.searchLocation(input) { coords ->
                if (coords != null) {
                    updateDestinationMarker(coords)
                    viewModel.fetchRoute(coords)
                } else {
                    binding.tvStatus.text = "Location not found."
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.currentLocation.observe(this) { coords ->
            binding.tvCurrentLocation.text = coords ?: "Tap the button to get your location"
            coords?.let { updateCurrentLocationMarker(it) }
        }

        viewModel.uiState.observe(this) { state ->
            renderState(state)
        }
    }

    private fun renderState(state: RouteUiState) {
        when (state) {
            is RouteUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = ""
                binding.tvDistance.text = "Distance: —"
                binding.tvDuration.text = "Duration: —"
                binding.tvSource.text = ""
            }
            is RouteUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvDistance.text = "Distance: ..."
                binding.tvDuration.text = "Duration: ..."
            }
            is RouteUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = ""
                binding.tvDistance.text = "Distance: %.2f km".format(state.distanceKm)
                binding.tvDuration.text = "Duration: %.1f min".format(state.durationMin)
                binding.tvSource.text = "Source: ${state.source}"
                drawRoute(state.geometry)
            }
            is RouteUiState.OfflineFallback -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = state.message
                binding.tvDistance.text = "Distance: %.2f km".format(state.distanceKm)
                binding.tvDuration.text = "Duration: %.1f min".format(state.durationMin)
                binding.tvSource.text = "Source: offline cache"
                drawRoute(state.geometry)
            }
            is RouteUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = state.message
                binding.tvDistance.text = "Distance: —"
                binding.tvDuration.text = "Duration: —"
            }
        }
    }

    private fun fetchLocation() {
        binding.tvStatus.text = ""
        binding.tvCurrentLocation.text = "Getting location..."

        lifecycleScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                if (location != null) {
                    val coords = "${location.longitude},${location.latitude}"
                    viewModel.setCurrentLocation(coords)
                } else {
                    binding.tvCurrentLocation.text = "Could not determine location"
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "Error: ${e.message}"
            }
        }
    }

    private fun updateCurrentLocationMarker(coords: String) {
        val parts = coords.split(",")
        if (parts.size != 2) return
        val lng = parts[0].toDoubleOrNull() ?: return
        val lat = parts[1].toDoubleOrNull() ?: return

        val point = GeoPoint(lat, lng)
        currentLocationMarker?.let { binding.mapView.overlays.remove(it) }

        val marker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "My Location"
            icon = icon?.constantState?.newDrawable()?.mutate()?.apply {
                setColorFilter(android.graphics.Color.BLUE, PorterDuff.Mode.SRC_IN)
            }
        }
        binding.mapView.overlays.add(marker)
        currentLocationMarker = marker
        binding.mapView.controller.animateTo(point)
        binding.mapView.invalidate()
    }

    private fun updateDestinationMarker(coords: String) {
        val parts = coords.split(",")
        if (parts.size != 2) return
        val lng = parts[0].toDoubleOrNull() ?: return
        val lat = parts[1].toDoubleOrNull() ?: return

        val point = GeoPoint(lat, lng)
        destinationMarker?.let { binding.mapView.overlays.remove(it) }

        val marker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Destination"
            icon = icon?.constantState?.newDrawable()?.mutate()?.apply {
                setColorFilter(android.graphics.Color.RED, PorterDuff.Mode.SRC_IN)
            }
        }
        binding.mapView.overlays.add(marker)
        destinationMarker = marker
        binding.mapView.invalidate()
    }

    private fun drawRoute(geometry: List<Pair<Double, Double>>) {
        routePolyline?.let { binding.mapView.overlays.remove(it) }
        if (geometry.isEmpty()) {
            binding.mapView.invalidate()
            return
        }

        val points = geometry.map { (lng, lat) -> GeoPoint(lat, lng) }
        val polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = android.graphics.Color.BLUE
            outlinePaint.strokeWidth = 10f
        }
        binding.mapView.overlays.add(polyline)
        routePolyline = polyline

        val bbox = BoundingBox.fromGeoPoints(points)
        binding.mapView.post {
            binding.mapView.zoomToBoundingBox(bbox, true, 100)
        }
        binding.mapView.invalidate()
    }
}