package com.univ.routeplanner.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.univ.routeplanner.data.api.GeocodeResponse
import com.univ.routeplanner.data.api.RetrofitClient
import com.univ.routeplanner.data.db.AppDatabase
import com.univ.routeplanner.data.repository.RouteRepository
import com.univ.routeplanner.util.NetworkHelper
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainViewModel(
    private val repository: RouteRepository,
    private val networkHelper: NetworkHelper
) : ViewModel() {

    private val _uiState = MutableLiveData<RouteUiState>(RouteUiState.Idle)
    val uiState: LiveData<RouteUiState> = _uiState

    private val _currentLocation = MutableLiveData<String?>(null)
    val currentLocation: LiveData<String?> = _currentLocation

    fun setCurrentLocation(coords: String) {
        _currentLocation.value = coords
    }

    fun searchLocation(query: String, onResult: (String?) -> Unit) {
        // Note: Changed from .instance to .api
        RetrofitClient.api.searchLocation(query).enqueue(object : Callback<GeocodeResponse> {
            override fun onResponse(call: Call<GeocodeResponse>, response: Response<GeocodeResponse>) {
                val feature = response.body()?.features?.firstOrNull()
                if (feature != null) {
                    // Ensure geometry and coordinates are not null
                    val coordsList: List<Double>? = feature.geometry?.coordinates
                    if (coordsList != null && coordsList.size >= 2) {
                        val lon = coordsList[0]
                        val lat = coordsList[1]
                        onResult("$lon,$lat")
                    }else {
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }

            override fun onFailure(call: Call<GeocodeResponse>, t: Throwable) {
                onResult(null)
            }
        })
    }
    fun fetchRoute(destination: String) {
        val origin = _currentLocation.value

        if (origin.isNullOrBlank()) {
            _uiState.value = RouteUiState.Error("Tap 'Get My Location' first to set origin.")
            return
        }
        if (destination.isBlank()) {
            _uiState.value = RouteUiState.Error("Please enter a destination.")
            return
        }

        _uiState.value = RouteUiState.Loading

        viewModelScope.launch {
            if (!networkHelper.isOnline()) {
                serveCachedOrError(message = "No internet — showing last saved route")
                return@launch
            }

            try {
                val result = repository.getRoute(origin, destination)
                _uiState.value = RouteUiState.Success(
                    distanceKm = result.distanceMeters / 1000.0,
                    durationMin = result.durationSeconds / 60.0,
                    source = result.source,
                    geometry = result.geometry
                )
            } catch (e: Exception) {
                serveCachedOrError(message = "Network error — showing last saved route")
            }
        }
    }

    private suspend fun serveCachedOrError(message: String) {
        val cached = repository.getLatestCachedRoute()
        _uiState.value = if (cached != null) {
            RouteUiState.OfflineFallback(
                distanceKm = cached.distanceMeters / 1000.0,
                durationMin = cached.durationSeconds / 60.0,
                message = message,
                geometry = cached.geometry
            )
        } else {
            RouteUiState.Error("$message (no cached data available).")
        }
    }

    fun loadLastCachedRoute() {
        viewModelScope.launch {
            val cached = repository.getLatestCachedRoute()
            if (cached != null) {
                _uiState.value = RouteUiState.Success(
                    distanceKm = cached.distanceMeters / 1000.0,
                    durationMin = cached.durationSeconds / 60.0,
                    source = cached.source,
                    geometry = cached.geometry
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _uiState.value = RouteUiState.Idle
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dao = AppDatabase.getInstance(context).routeDao()
            val repository = RouteRepository(dao = dao)
            val networkHelper = NetworkHelper(context)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, networkHelper) as T
        }
    }
}