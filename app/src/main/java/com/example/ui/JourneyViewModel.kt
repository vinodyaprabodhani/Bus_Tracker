package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.JourneyEntity
import com.example.data.JourneyRepository
import com.example.model.BusRoute
import com.example.model.BusRouteData
import com.example.model.RouteStop
import com.example.util.LocationUtils
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

sealed interface JourneyStatus {
    object Idle : JourneyStatus
    object Tracking : JourneyStatus
    object Finished : JourneyStatus
}

data class TrackingState(
    val status: JourneyStatus = JourneyStatus.Idle,
    val currentLat: Double = 6.9344, // Default Colombo
    val currentLng: Double = 79.8518,
    val currentSpeedKmh: Double = 0.0,
    val distanceTraveledKm: Double = 0.0,
    val calculatedFareLkr: Double = 0.0,
    val durationSeconds: Long = 0,
    val accuracyMeters: Float = 0f,
    val activeRoute: BusRoute? = null,
    val isSimulationMode: Boolean = true,
    val activeStopIndex: Int = 0,
    val stopsPassed: List<RouteStop> = emptyList(),
    val busType: String = "Private", // "CTB" or "Private"
    val busNumber: String = "",
    val customFromStation: String = "",
    val customToStation: String = "",
    val isCustomRoute: Boolean = false,
    val hasNotifiedArrival: Boolean = false,
    val notificationTitle: String? = null,
    val notificationMessage: String? = null
)

class JourneyViewModel(private val repository: JourneyRepository, context: Context) : ViewModel() {

    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _savedJourneys = MutableStateFlow<List<JourneyEntity>>(emptyList())
    val savedJourneys: StateFlow<List<JourneyEntity>> = _savedJourneys.asStateFlow()

    // Dynamic Language Toggle: "en" and "si"
    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    // Accessibility Text Size multiplier
    private val _textSizeMultiplier = MutableStateFlow(1.0f)
    val textSizeMultiplier: StateFlow<Float> = _textSizeMultiplier.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var trackingJob: Job? = null
    private var durationJob: Job? = null
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    init {
        loadHistory()
        // Select standard route initially (Route 138)
        _trackingState.value = _trackingState.value.copy(activeRoute = BusRouteData.sampleRoutes[0])
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.allJourneys.collect { list ->
                _savedJourneys.value = list
            }
        }
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == "en") "si" else "en"
    }

    fun toggleTextSize() {
        _textSizeMultiplier.value = if (_textSizeMultiplier.value == 1.0f) 1.25f else 1.0f
    }

    fun selectRoute(route: BusRoute) {
        if (_trackingState.value.status != JourneyStatus.Tracking) {
            _trackingState.value = _trackingState.value.copy(
                activeRoute = route,
                currentLat = route.stops.firstOrNull()?.latitude ?: 6.9344,
                currentLng = route.stops.firstOrNull()?.longitude ?: 79.8518,
                distanceTraveledKm = 0.0,
                calculatedFareLkr = route.baseFareLkr,
                isCustomRoute = false
            )
        }
    }

    fun toggleSimulationMode(isSimulated: Boolean) {
        if (_trackingState.value.status != JourneyStatus.Tracking) {
            _trackingState.value = _trackingState.value.copy(isSimulationMode = isSimulated)
        }
    }

    fun startJourney() {
        val currState = _trackingState.value
        if (currState.status == JourneyStatus.Tracking) return

        val initialFare = currState.activeRoute?.baseFareLkr ?: 30.0
        val startLat = currState.activeRoute?.stops?.firstOrNull()?.latitude ?: currState.currentLat
        val startLng = currState.activeRoute?.stops?.firstOrNull()?.longitude ?: currState.currentLng

        _trackingState.value = currState.copy(
            status = JourneyStatus.Tracking,
            distanceTraveledKm = 0.0,
            calculatedFareLkr = initialFare,
            durationSeconds = 0,
            currentLat = startLat,
            currentLng = startLng,
            activeStopIndex = 0,
            stopsPassed = if (currState.activeRoute != null && currState.activeRoute.stops.isNotEmpty()) listOf(currState.activeRoute.stops[0]) else emptyList()
        )

        lastLocation = null

        // Start timer
        startDurationTimer()

        if (_trackingState.value.isSimulationMode) {
            startSimulation()
        } else {
            startRealGpsTracking()
        }
    }

    fun stopJourney() {
        val state = _trackingState.value
        if (state.status != JourneyStatus.Tracking) return

        // Stop tracking mechanics
        stopDurationTimer()
        stopGpsUpdates()
        trackingJob?.cancel()

        _trackingState.value = state.copy(status = JourneyStatus.Finished)

        // Save completed journey directly to SQLite Room Database
        viewModelScope.launch {
            val routeNameEn = state.activeRoute?.englishTitle ?: "Custom Map Route"
            val startStopEn = state.activeRoute?.stops?.firstOrNull()?.englishName ?: "Checked In Point"
            val endStopEn = state.activeRoute?.stops?.lastOrNull()?.englishName ?: "Checked Out Point"
            
            val routeNameSi = state.activeRoute?.sinhalaTitle ?: "ස්වයංක්‍රීය මාර්ගය"
            val startStopSi = state.activeRoute?.stops?.firstOrNull()?.sinhalaName ?: "ඇතුල්වීමේ ස්ථානය"
            val endStopSi = state.activeRoute?.stops?.lastOrNull()?.sinhalaName ?: "පිටවීමේ ස්ථානය"

            val fromStr = if (_language.value == "en") "$startStopEn ($routeNameEn)" else "$startStopSi ($routeNameSi)"
            val toStr = if (_language.value == "en") {
                if (state.isSimulationMode) endStopEn else "Live Drop Point"
            } else {
                if (state.isSimulationMode) endStopSi else "සජීවී බැසීමේ ස්ථානය"
            }

            repository.insert(
                JourneyEntity(
                    fromStation = fromStr,
                    toStation = toStr,
                    distanceKm = state.distanceTraveledKm,
                    fareLkr = state.calculatedFareLkr,
                    durationMinutes = (state.durationSeconds / 60) + 1,
                    busCategory = state.activeRoute?.category ?: "Normal",
                    busType = state.busType,
                    busNumber = state.busNumber
                )
            )
        }
    }

    fun selectBusType(type: String) {
        _trackingState.value = _trackingState.value.copy(busType = type)
    }

    fun setBusNumber(number: String) {
        _trackingState.value = _trackingState.value.copy(busNumber = number)
    }

    fun dismissNotification() {
        _trackingState.value = _trackingState.value.copy(
            notificationTitle = null,
            notificationMessage = null
        )
    }

    // SLTB Known Ceylon Cities/Stations Lookup Database
    val knownStations = listOf(
        Pair("Colombo Fort", Pair(6.9344, 79.8518)),
        Pair("Pettah", Pair(6.9360, 79.8525)),
        Pair("Maharagama", Pair(6.8481, 79.9265)),
        Pair("Kandy", Pair(7.2906, 80.6337)),
        Pair("Galle", Pair(6.0331, 80.2152)),
        Pair("Matara", Pair(5.9496, 80.5469)),
        Pair("Horana", Pair(6.7126, 80.0621)),
        Pair("Nugegoda", Pair(6.8741, 79.8893)),
        Pair("Piliyandala", Pair(6.7997, 79.9230)),
        Pair("Kurunegala", Pair(7.4818, 80.3609)),
        Pair("Anuradhapura", Pair(8.3114, 80.4037)),
        Pair("Jaffna", Pair(9.6615, 80.0125)),
        Pair("Trincomalee", Pair(8.5711, 81.2335)),
        Pair("Batticaloa", Pair(7.7102, 81.6924)),
        Pair("Nuwara Eliya", Pair(6.9680, 80.7845)),
        Pair("Badulla", Pair(6.9934, 81.0550)),
        Pair("Ratnapura", Pair(6.6828, 80.3992)),
        Pair("Negombo", Pair(7.2089, 79.8350)),
        Pair("Gampaha", Pair(7.0897, 79.9911)),
        Pair("Kegalle", Pair(7.2513, 80.3458)),
        Pair("Warakapola", Pair(7.2215, 80.1985)),
        Pair("Mawanella", Pair(7.2526, 80.4485)),
        Pair("Kadugannawa", Pair(7.2541, 80.5212)),
        Pair("Peradeniya", Pair(7.2685, 80.5925)),
        Pair("Peliyagoda", Pair(6.9680, 79.8820)),
        Pair("Kadawatha", Pair(7.0016, 79.9531)),
        Pair("Yakkala", Pair(7.0911, 80.0315))
    )

    private val sinhalaStationNames = mapOf(
        "colombo fort" to "කොළඹ කොටුව",
        "pettah" to "පිටකොටුව",
        "maharagama" to "මහරගම",
        "kandy" to "මහනුවර",
        "galle" to "ගාල්ල",
        "matara" to "මාතර",
        "horana" to "හොරණ",
        "nugegoda" to "නුගේගොඩ",
        "piliyandala" to "පිළියන්දල",
        "kurunegala" to "කුරුණෑගල",
        "anuradhapura" to "අනුරාධපුරය",
        "jaffna" to "යාපනය",
        "trincomalee" to "ත්‍රිකුණාමලය",
        "batticaloa" to "මඩකලපුව",
        "nuwara eliya" to "නුවරඑළිය",
        "badulla" to "බදුල්ල",
        "ratnapura" to "රත්නපුරය",
        "negombo" to "මීගමුව",
        "gampaha" to "ගම්පහ",
        "kegalle" to "කෑගල්ල",
        "warakapola" to "වරකාපොල",
        "mawanella" to "මාවනැල්ල",
        "kadugannawa" to "කඩුගන්නාව",
        "peradeniya" to "පේරාදෙනිය",
        "peliyagoda" to "පෑලියගොඩ",
        "kadawatha" to "කඩවත",
        "yakkala" to "යක්කල"
    )

    fun getCoordinatesForPlace(placeName: String): Pair<Double, Double> {
        val found = knownStations.firstOrNull { it.first.equals(placeName.trim(), ignoreCase = true) }
        if (found != null) {
            return found.second
        }
        val hash = Math.abs(placeName.trim().hashCode())
        val lat = 6.0 + (hash % 300) / 100.0 // Coordinates roughly within safe Sri Lanka zone
        val lng = 79.8 + (hash % 150) / 100.0
        return Pair(lat, lng)
    }

    fun getSinhalaNameForPlace(placeName: String): String {
        return sinhalaStationNames[placeName.trim().lowercase()] ?: placeName.trim()
    }

    fun selectCustomJourney(from: String, to: String, category: String) {
        val fromClean = from.trim()
        val toClean = to.trim()
        if (fromClean.isBlank() || toClean.isBlank()) return

        val fromCoords = getCoordinatesForPlace(fromClean)
        val toCoords = getCoordinatesForPlace(toClean)

        val totalDistance = LocationUtils.calculateDistanceKm(
            fromCoords.first, fromCoords.second,
            toCoords.first, toCoords.second
        ).coerceAtLeast(1.0)

        // Build mock stops sequentially to draw on map & simulate beautifully
        val finalStops = mutableListOf<RouteStop>()
        finalStops.add(RouteStop(fromClean, getSinhalaNameForPlace(fromClean), fromCoords.first, fromCoords.second, 0.0))

        val waypointCount = 2
        for (i in 1..waypointCount) {
            val fraction = i.toDouble() / (waypointCount + 1)
            val lat = fromCoords.first + (toCoords.first - fromCoords.first) * fraction
            val lng = fromCoords.second + (toCoords.second - fromCoords.second) * fraction
            val distOffset = totalDistance * fraction
            val stopNameEn = "$fromClean Stage $i"
            val stopNameSi = "${getSinhalaNameForPlace(fromClean)} $i වන සීමාව"
            finalStops.add(RouteStop(stopNameEn, stopNameSi, lat, lng, distOffset))
        }

        finalStops.add(RouteStop(toClean, getSinhalaNameForPlace(toClean), toCoords.first, toCoords.second, totalDistance))

        val baseFare = if (category == "Semi-Luxury") 45.0 else if (category == "AC-Expressway") 120.0 else 30.0
        val perKmRate = if (category == "Semi-Luxury") 14.0 else if (category == "AC-Expressway") 22.0 else 9.5

        val customRoute = BusRoute(
            routeNumber = if (category == "AC-Expressway") "EX-GEN" else if (category == "Semi-Luxury") "SL-GEN" else "01-GEN",
            englishTitle = "$fromClean - $toClean",
            sinhalaTitle = "${getSinhalaNameForPlace(fromClean)} - ${getSinhalaNameForPlace(toClean)}",
            startStationEnglish = fromClean,
            startStationSinhala = getSinhalaNameForPlace(fromClean),
            endStationEnglish = toClean,
            endStationSinhala = getSinhalaNameForPlace(toClean),
            category = category,
            baseFareLkr = baseFare,
            perKmRateLkr = perKmRate,
            stops = finalStops
        )

        _trackingState.value = _trackingState.value.copy(
            activeRoute = customRoute,
            currentLat = fromCoords.first,
            currentLng = fromCoords.second,
            distanceTraveledKm = 0.0,
            calculatedFareLkr = baseFare,
            customFromStation = fromClean,
            customToStation = toClean,
            isCustomRoute = true,
            hasNotifiedArrival = false,
            notificationTitle = null,
            notificationMessage = null
        )
    }

    fun resetJourney() {
        val oldState = _trackingState.value
        val route = oldState.activeRoute ?: BusRouteData.sampleRoutes[0]
        _trackingState.value = TrackingState(
            status = JourneyStatus.Idle,
            activeRoute = route,
            currentLat = route.stops.firstOrNull()?.latitude ?: 6.9344,
            currentLng = route.stops.firstOrNull()?.longitude ?: 79.8518,
            calculatedFareLkr = route.baseFareLkr,
            busType = oldState.busType,
            busNumber = oldState.busNumber,
            customFromStation = oldState.customFromStation,
            customToStation = oldState.customToStation,
            isCustomRoute = oldState.isCustomRoute,
            hasNotifiedArrival = false,
            notificationTitle = null,
            notificationMessage = null
        )
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _trackingState.value = _trackingState.value.copy(
                    durationSeconds = _trackingState.value.durationSeconds + 1
                )
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    // SIMULATION MECHANICS
    private fun startSimulation() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            val route = _trackingState.value.activeRoute ?: return@launch
            val stops = route.stops
            if (stops.size < 2) return@launch

            var currentStopIdx = 0
            val subSegments = 10 // break each stop-to-stop into 10 steps for smooth tracking simulation

            while (currentStopIdx < stops.size - 1) {
                val startStop = stops[currentStopIdx]
                val endStop = stops[currentStopIdx + 1]

                for (step in 1..subSegments) {
                    delay(1200) // update speed and coordinates smoothly every 1.2s

                    val fraction = step.toDouble() / subSegments
                    val simLat = startStop.latitude + (endStop.latitude - startStop.latitude) * fraction
                    val simLng = startStop.longitude + (endStop.longitude - startStop.longitude) * fraction

                    // Calculate simulated cumulative distance
                    val baseOffset = startStop.distanceKmOffset
                    val segmentDistance = endStop.distanceKmOffset - startStop.distanceKmOffset
                    val subDist = baseOffset + (segmentDistance * fraction)

                    // Speed simulation
                    val simSpeed = if (step == subSegments) 0.0 else (35 + (0..25).random()).toDouble()

                    // Calculate fare dynamically
                    val simulatedFare = calculateDynamicFare(subDist, route)

                    // Stops passed list
                    val passed = stops.subList(0, currentStopIdx + 1) + if (step == subSegments) listOf(endStop) else emptyList()

                    val finalStop = stops.last()
                    val distToFinal = LocationUtils.calculateDistanceKm(simLat, simLng, finalStop.latitude, finalStop.longitude)
                    val isNear = distToFinal < 1.0 && !_trackingState.value.hasNotifiedArrival

                    _trackingState.value = _trackingState.value.copy(
                        currentLat = simLat,
                        currentLng = simLng,
                        currentSpeedKmh = simSpeed,
                        distanceTraveledKm = subDist,
                        calculatedFareLkr = simulatedFare,
                        accuracyMeters = 3.5f,
                        activeStopIndex = if (step == subSegments) currentStopIdx + 1 else currentStopIdx,
                        stopsPassed = passed.distinct(),
                        hasNotifiedArrival = _trackingState.value.hasNotifiedArrival || isNear,
                        notificationTitle = if (isNear) {
                            if (_language.value == "si") "ගමනාන්තය ආසන්නයි! 🔔" else "Destination Near! 🔔"
                        } else _trackingState.value.notificationTitle,
                        notificationMessage = if (isNear) {
                            if (_language.value == "si") {
                                "ඔබගේ බස් රථය ${finalStop.sinhalaName} ආසන්නයට පැමිණෙමින් තිබේ. කරුණාකර බැසීමට සූදානම් වන්න!"
                            } else {
                                "Your bus is approaching ${finalStop.englishName}. Please prepare to get off!"
                            }
                        } else _trackingState.value.notificationMessage
                    )
                }
                currentStopIdx++
            }
            // Journey complete
            stopJourney()
        }
    }

    // REAL GPS MECHANICS
    @SuppressLint("MissingPermission")
    private fun startRealGpsTracking() {
        stopGpsUpdates()
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1500)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                
                val sourceRoute = _trackingState.value.activeRoute
                val isFirstUpdate = lastLocation == null
                
                var currentDistance = _trackingState.value.distanceTraveledKm
                if (!isFirstUpdate && lastLocation != null) {
                    val incrementalKm = LocationUtils.calculateDistanceKm(
                        lastLocation!!.latitude, lastLocation!!.longitude,
                        location.latitude, location.longitude
                    )
                    // filter out tiny GPS fluctuations or errors
                    if (location.accuracy < 30 && incrementalKm > 0.005) {
                        currentDistance += incrementalKm
                    }
                }

                lastLocation = location

                val speedKmh = if (location.hasSpeed()) {
                    location.speed * 3.6
                } else {
                    if (isFirstUpdate) 0.0 else (15..55).random().toDouble()
                }

                val fare = calculateDynamicFare(currentDistance, sourceRoute)

                // Detect nearest stops passed
                val passedStops = mutableListOf<RouteStop>()
                var hasNotified = _trackingState.value.hasNotifiedArrival
                var notTitle = _trackingState.value.notificationTitle
                var notMsg = _trackingState.value.notificationMessage

                if (sourceRoute != null && sourceRoute.stops.isNotEmpty()) {
                    for (stop in sourceRoute.stops) {
                        val distToStop = LocationUtils.calculateDistanceKm(
                            location.latitude, location.longitude,
                            stop.latitude, stop.longitude
                        )
                        // If center is within 400 meters of the stop coordinate, consider it passed
                        if (distToStop < 0.4) {
                            passedStops.add(stop)
                        }
                    }

                    // Proximity warning for the last stop (arrival place)
                    if (!hasNotified) {
                        val finalStop = sourceRoute.stops.last()
                        val distToFinal = LocationUtils.calculateDistanceKm(
                            location.latitude, location.longitude,
                            finalStop.latitude, finalStop.longitude
                        )
                        if (distToFinal < 1.0) {
                            hasNotified = true
                            notTitle = if (_language.value == "si") "ගමනාන්තය ආසන්නයි! 🔔" else "Destination Near! 🔔"
                            notMsg = if (_language.value == "si") {
                                "ඔබගේ බස් රථය ${finalStop.sinhalaName} ආසන්නයට පැමිණෙමින් තිබේ. කරුණාකර බැසීමට සූදානම් වන්න!"
                            } else {
                                "Your bus is approaching ${finalStop.englishName}. Please prepare to get off!"
                            }
                        }
                    }
                }

                _trackingState.value = _trackingState.value.copy(
                    currentLat = location.latitude,
                    currentLng = location.longitude,
                    currentSpeedKmh = speedKmh,
                    distanceTraveledKm = currentDistance,
                    calculatedFareLkr = fare,
                    accuracyMeters = location.accuracy,
                    stopsPassed = (_trackingState.value.stopsPassed + passedStops).distinct(),
                    hasNotifiedArrival = hasNotified,
                    notificationTitle = notTitle,
                    notificationMessage = notMsg
                )
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopGpsUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        locationCallback = null
    }

    private fun calculateDynamicFare(distanceKm: Double, route: BusRoute?): Double {
        if (distanceKm <= 0.0) return route?.baseFareLkr ?: 30.0
        
        val baseFare = route?.baseFareLkr ?: 30.0
        val perKmRate = route?.perKmRateLkr ?: 9.5

        return if (distanceKm <= 2.0) {
            baseFare
        } else {
            val excessKm = distanceKm - 2.0
            val rawFare = baseFare + (excessKm * perKmRate)
            // Round to nearest integer for realistic CTB/NTC ticket change prices
            (rawFare * 1.0).roundToInt().toDouble()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopGpsUpdates()
    }
}

class JourneyViewModelFactory(
    private val repository: JourneyRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JourneyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JourneyViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
