package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.JourneyEntity
import com.example.data.JourneyRepository
import com.example.model.BusRoute
import com.example.model.BusRouteData
import com.example.model.RouteStop
import com.example.ui.*
import com.example.ui.components.OfflineRouteMap
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocationUtils
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room Database repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = JourneyRepository(database.journeyDao())

        setContent {
            MyApplicationTheme {
                // Instantiating custom state view model
                val appViewModel: JourneyViewModel = viewModel(
                    factory = JourneyViewModelFactory(repository, this)
                )

                PassengerAppContent(viewModel = appViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerAppContent(viewModel: JourneyViewModel) {
    val context = LocalContext.current
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val savedJourneys by viewModel.savedJourneys.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val textScale by viewModel.textSizeMultiplier.collectAsStateWithLifecycle()

    val isSinhala = language == "si"
    var activeTab by remember { mutableStateOf(0) } // 0: Journey, 1: History, 2: Fare Guide

    // Checking location permissions dynamically
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
        if (hasLocationPermission) {
            Toast.makeText(
                context,
                if (isSinhala) "ස්ථාන අවසර ලබා දෙන ලදී!" else "GPS Location Permissions Granted!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                if (isSinhala) "GPS ක්‍රියාත්මක කිරීමට අවසර අවශ්‍යයි" else "Location access is required for real GPS tracking.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Direct permission check logic when live GPS tracking mode is enabled
    LaunchedEffect(trackingState.isSimulationMode) {
        if (!trackingState.isSimulationMode && !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Sleek Brand Logo Icon (SLTB Theme)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF9E1C1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Sleek Header Information
                        Column {
                            Text(
                                text = if (isSinhala) "ලංකා Bus Go" else "Lanka Bus Go",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9E1C1C),
                                fontSize = (15 * textScale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (trackingState.isSimulationMode) {
                                    if (isSinhala) "ක්‍රියාකාරී • ඩෙමෝ GPS" else "GPS SIMULATOR • DEMO"
                                } else {
                                    if (isSinhala) "ක්‍රියාකාරී • සැබෑ GPS" else "GPS ACTIVE • ONLINE"
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = if (trackingState.isSimulationMode) Color(0xFFD32F2F) else Color(0xFF059669),
                                fontSize = (10 * textScale).sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    // Quick Accessibility Text Sizer Action Button (A+)
                    IconButton(
                        onClick = { viewModel.toggleTextSize() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .border(
                                width = if (textScale > 1.0f) 2.dp else 1.dp,
                                color = if (textScale > 1.0f) Color(0xFF001D35) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .testTag("accessibility_size_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "A",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF001D35)
                            )
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase Text Size",
                                modifier = Modifier.size(9.dp),
                                tint = Color(0xFF2563EB)
                            )
                        }
                    }

                    // Segmented Dual Pill Language Switcher
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .padding(2.dp)
                            .testTag("language_toggle_button")
                    ) {
                        // EN Tab
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (!isSinhala) Color.White else Color.Transparent)
                                .clickable { if (isSinhala) viewModel.toggleLanguage() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "EN",
                                color = if (!isSinhala) Color(0xFF001D35) else Color(0xFF64748B),
                                fontWeight = if (!isSinhala) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                        // SI Tab
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSinhala) Color.White else Color.Transparent)
                                .clickable { if (!isSinhala) viewModel.toggleLanguage() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "සිං",
                                color = if (isSinhala) Color(0xFF001D35) else Color(0xFF64748B),
                                fontWeight = if (isSinhala) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.border(width = 0.5.dp, color = Color(0xFFF1F5F9))
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = Color.White,
                modifier = Modifier.border(width = 0.5.dp, color = Color(0xFFF1F5F9))
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.DirectionsBus, contentDescription = "Journey") },
                    label = {
                        Text(
                            text = if (isSinhala) "වත්මන් ගමන" else "Live Journey",
                            fontSize = (11 * textScale).sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001D35),
                        selectedTextColor = Color(0xFF001D35),
                        indicatorColor = Color(0xFFD3E3FD),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("nav_tab_journey")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = {
                        Text(
                            text = if (isSinhala) "ගමන් ඉතිහාසය" else "Trip Log",
                            fontSize = (11 * textScale).sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001D35),
                        selectedTextColor = Color(0xFF001D35),
                        indicatorColor = Color(0xFFD3E3FD),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("nav_tab_history")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = "Fare Guide") },
                    label = {
                        Text(
                            text = if (isSinhala) "ගාස්තු ලේඛනය" else "Fare Reference",
                            fontSize = (11 * textScale).sp,
                            fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF001D35),
                        selectedTextColor = Color(0xFF001D35),
                        indicatorColor = Color(0xFFD3E3FD),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("nav_tab_guide")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // SLTB PROXIMITY ARRIVAL NOTIFICATION ALERT DIALOG
            if (trackingState.notificationTitle != null && trackingState.notificationMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNotification() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Notification Bell",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = trackingState.notificationTitle ?: "",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9E1C1C),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = trackingState.notificationMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissNotification() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9E1C1C),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isSinhala) "ලැබුණි / මම සූදානම්" else "Acknowledge",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("proximity_notification_dialog")
                )
            }

            when (activeTab) {
                0 -> JourneyTabScreen(
                    state = trackingState,
                    viewModel = viewModel,
                    isSinhala = isSinhala,
                    textScale = textScale,
                    hasPermission = hasLocationPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
                1 -> HistoryTabScreen(
                    journeys = savedJourneys,
                    viewModel = viewModel,
                    isSinhala = isSinhala,
                    textScale = textScale
                )
                2 -> FareGuideTabScreen(
                    isSinhala = isSinhala,
                    textScale = textScale
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyTabScreen(
    state: TrackingState,
    viewModel: JourneyViewModel,
    isSinhala: Boolean,
    textScale: Float,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var showRouteMenu by remember { mutableStateOf(false) }
    var isCustomTabSelected by remember { mutableStateOf(state.isCustomRoute) }
    var fromText by remember { mutableStateOf(state.customFromStation.ifBlank { "Colombo Fort" }) }
    var toText by remember { mutableStateOf(state.customToStation.ifBlank { "Kandy" }) }
    var customServiceClass by remember { mutableStateOf(state.activeRoute?.category ?: "Normal") }
    var showCategoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.isCustomRoute) {
        isCustomTabSelected = state.isCustomRoute
    }

    LaunchedEffect(Unit) {
        if (state.customFromStation.isBlank() && state.customToStation.isBlank()) {
            viewModel.selectCustomJourney("Colombo Fort", "Kandy", customServiceClass)
        }
    }

    LaunchedEffect(state.customFromStation, state.customToStation) {
        if (state.customFromStation.isNotBlank() && state.customFromStation != fromText) {
            fromText = state.customFromStation
        }
        if (state.customToStation.isNotBlank() && state.customToStation != toText) {
            toText = state.customToStation
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. ROUTE SELECTOR PANEL (Choose from predefined lists)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFFE2E8F0),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isSinhala) "මාර්ගය තෝරාගැනීම" else "ACTIVE TRANSIT ROUTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E1C1C),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = (10 * textScale).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            // Segment buttons to choose Standard Mode vs Anywhere Mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { isCustomTabSelected = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isCustomTabSelected) Color(0xFF9E1C1C) else Color.Transparent,
                                        contentColor = if (!isCustomTabSelected) Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (isSinhala) "ප්‍රධාන මාර්ග" else "Standard Routes",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (11 * textScale).sp
                                    )
                                }
                                Button(
                                    onClick = { isCustomTabSelected = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCustomTabSelected) Color(0xFF9E1C1C) else Color.Transparent,
                                        contentColor = if (isCustomTabSelected) Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (isSinhala) "ඕනෑම ස්ථානයක්" else "Anywhere (Custom)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (11 * textScale).sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (!isCustomTabSelected) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFF8F9FF))
                                            .clickable(enabled = state.status != JourneyStatus.Tracking) {
                                                showRouteMenu = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        Color(0xFFDC2626),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = state.activeRoute?.routeNumber ?: "BUS",
                                                    fontWeight = FontWeight.Black,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    fontSize = (13 * textScale).sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = if (isSinhala) {
                                                        state.activeRoute?.sinhalaTitle ?: "ස්වයංක්‍රීය ගමනාන්තය"
                                                    } else {
                                                        state.activeRoute?.englishTitle ?: "Custom Navigation Path"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFF0F172A),
                                                    fontSize = (13 * textScale).sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isSinhala) {
                                                        "කාණ්ඩය: ${translateCategory(state.activeRoute?.category ?: "Custom", true)}"
                                                    } else {
                                                        "Bus Category: ${state.activeRoute?.category ?: "Custom"}"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF64748B),
                                                    fontSize = (11 * textScale).sp
                                                )
                                            }
                                        }

                                        if (state.status != JourneyStatus.Tracking) {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Expand Routes Dropdown",
                                                tint = Color(0xFF001D35)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showRouteMenu,
                                        onDismissRequest = { showRouteMenu = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .background(Color.White)
                                    ) {
                                        BusRouteData.sampleRoutes.forEach { route ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    Color(0xFFDC2626),
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                route.routeNumber,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                color = Color.White
                                                            )
                                                        }
                                                        Text(
                                                            text = if (isSinhala) route.sinhalaTitle else route.englishTitle,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF0F172A),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.selectRoute(route)
                                                    showRouteMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // CUSTOM ANY PLACE IN SRI LANKA FROM-TO CONTROLLER
                                Text(
                                    text = if (isSinhala) "ශ්‍රී ලංකාවේ ඕනෑම ස්ථානයක් තෝරන්න" else "SELECT ANY STATIONS IN SRI LANKA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9E1C1C),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.0.sp,
                                    fontSize = (10 * textScale).sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Dynamic preset path selector pills
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    val presets = listOf(
                                        Pair("Colombo Fort", "Kandy"),
                                        Pair("Maharagama", "Galle"),
                                        Pair("Colombo Fort", "Horana"),
                                        Pair("Pettah", "Maharagama"),
                                        Pair("Colombo Fort", "Negombo"),
                                        Pair("Kandy", "Galle")
                                    )
                                    items(presets) { preset ->
                                        val label = if (isSinhala) {
                                            "${viewModel.getSinhalaNameForPlace(preset.first)} ⇄ ${viewModel.getSinhalaNameForPlace(preset.second)}"
                                        } else {
                                            "${preset.first} ⇄ ${preset.second}"
                                        }
                                        AssistChip(
                                            onClick = {
                                                if (state.status != JourneyStatus.Tracking) {
                                                    fromText = preset.first
                                                    toText = preset.second
                                                    viewModel.selectCustomJourney(preset.first, preset.second, customServiceClass)
                                                }
                                            },
                                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color(0xFFEFF6FF),
                                                labelColor = Color(0xFF1E40AF)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // From Place Field
                                OutlinedTextField(
                                    value = fromText,
                                    onValueChange = {
                                        if (state.status != JourneyStatus.Tracking) {
                                            fromText = it
                                        }
                                    },
                                    label = { Text(if (isSinhala) "ආරම්භක පර්යන්තය (FROM)" else "From Station / Town") },
                                    placeholder = { Text("e.g. Colombo Fort, Pettah, Maharagama") },
                                    leadingIcon = { Icon(Icons.Default.TripOrigin, contentDescription = null, tint = Color(0xFF059669)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_from_input"),
                                    enabled = state.status != JourneyStatus.Tracking,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = Color(0xFF9E1C1C),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedLabelColor = Color(0xFF9E1C1C),
                                        unfocusedLabelColor = Color(0xFF64748B)
                                    )
                                )

                                // From Autocomplete Suggestions
                                val fromClean = fromText.trim()
                                if (fromClean.isNotEmpty() && !viewModel.knownStations.any { it.first.equals(fromClean, ignoreCase = true) }) {
                                    val matches = viewModel.knownStations.filter {
                                        it.first.contains(fromClean, ignoreCase = true)
                                    }.take(4)
                                    if (matches.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            items(matches) { match ->
                                                val displayName = if (isSinhala) {
                                                    viewModel.getSinhalaNameForPlace(match.first)
                                                } else {
                                                    match.first
                                                }
                                                AssistChip(
                                                    onClick = {
                                                        fromText = match.first
                                                    },
                                                    label = { Text(displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = Color(0xFFF1F5F9),
                                                        labelColor = Color(0xFF1E293B)
                                                    ),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // To Place Field
                                OutlinedTextField(
                                    value = toText,
                                    onValueChange = {
                                        if (state.status != JourneyStatus.Tracking) {
                                            toText = it
                                        }
                                    },
                                    label = { Text(if (isSinhala) "ගමනාන්තය (TO)" else "To Destination City") },
                                    placeholder = { Text("e.g. Kandy, Galle, Matara") },
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFDC2626)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_to_input"),
                                    enabled = state.status != JourneyStatus.Tracking,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = Color(0xFF9E1C1C),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedLabelColor = Color(0xFF9E1C1C),
                                        unfocusedLabelColor = Color(0xFF64748B)
                                    )
                                )

                                // To Autocomplete Suggestions
                                val toClean = toText.trim()
                                if (toClean.isNotEmpty() && !viewModel.knownStations.any { it.first.equals(toClean, ignoreCase = true) }) {
                                    val matches = viewModel.knownStations.filter {
                                        it.first.contains(toClean, ignoreCase = true)
                                    }.take(4)
                                    if (matches.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            items(matches) { match ->
                                                val displayName = if (isSinhala) {
                                                    viewModel.getSinhalaNameForPlace(match.first)
                                                } else {
                                                    match.first
                                                }
                                                AssistChip(
                                                    onClick = {
                                                        toText = match.first
                                                    },
                                                    label = { Text(displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = Color(0xFFF1F5F9),
                                                        labelColor = Color(0xFF1E293B)
                                                    ),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Category Dropdown Selection
                                Text(
                                    text = if (isSinhala) "සේවා වර්ගය" else "SERVICE QUALITY LEVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (9 * textScale).sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF8F9FF))
                                            .clickable(enabled = state.status != JourneyStatus.Tracking) {
                                                showCategoryMenu = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFF59E0B))
                                            Text(
                                                text = if (isSinhala) translateCategory(customServiceClass, true) else customServiceClass,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF9E1C1C))
                                    }

                                    DropdownMenu(
                                        expanded = showCategoryMenu,
                                        onDismissRequest = { showCategoryMenu = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .background(Color.White)
                                    ) {
                                        listOf("Normal", "Semi-Luxury", "AC-Expressway").forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (isSinhala) translateCategory(mode, true) else mode,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                },
                                                onClick = {
                                                    customServiceClass = mode
                                                    showCategoryMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // SEARCH & PLAN CUSTOM ROUTE BUTTON (Solid Iconic Ceylon Red)
                                Button(
                                    onClick = {
                                        if (state.status != JourneyStatus.Tracking) {
                                            viewModel.selectCustomJourney(fromText, toText, customServiceClass)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("submit_custom_search_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = fromText.isNotBlank() && toText.isNotBlank()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search and Plan Journey",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isSinhala) "මාර්ගය සැලසුම් කරන්න" else "SEARCH & PLAN ROUTE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isSinhala) "බස් රථයේ වර්ගය" else "BUS SERVICE TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = (9 * textScale).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CTB Option
                        val isCtb = state.busType == "CTB"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCtb) Color(0xFFDC2626) else Color(0xFFF1F5F9)
                                )
                                .clickable(enabled = state.status != JourneyStatus.Tracking) {
                                    viewModel.selectBusType("CTB")
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = if (isCtb) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isSinhala) "ලං.ග.ම. බස් (CTB)" else "CTB Bus (Govt)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCtb) Color.White else Color(0xFF0F172A),
                                    fontSize = (12 * textScale).sp
                                )
                            }
                        }

                        // Private Option
                        val isPrivate = state.busType == "Private"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPrivate) Color(0xFF2563EB) else Color(0xFFF1F5F9)
                                )
                                .clickable(enabled = state.status != JourneyStatus.Tracking) {
                                    viewModel.selectBusType("Private")
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Commute,
                                    contentDescription = null,
                                    tint = if (isPrivate) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isSinhala) "පෞද්ගලික බස්" else "Private Bus",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrivate) Color.White else Color(0xFF0F172A),
                                    fontSize = (12 * textScale).sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. LIVE OFFLINE MAP GRAPHIC CANVAS OVERLAY
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isSinhala) "සබැඳි නොවන මාර්ග සිතියම" else "OFFLINE ROUTE GEOPATH MAP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = (11 * textScale).sp
                )
                
                OfflineRouteMap(
                    activeRoute = state.activeRoute,
                    currentLat = state.currentLat,
                    currentLng = state.currentLng,
                    isSinhala = isSinhala,
                    modifier = Modifier.testTag("offline_canvas_map")
                )
            }
        }

        // 3. TRACKING OPTION CONTROLLER (Live GPS vs Demo simulation Mode Toggle)
        if (state.status != JourneyStatus.Tracking) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isSinhala) "සජීවී GPS ධාවනය (Demo නොව සැබෑ)" else "Use Live Device GPS Coordinate",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (11 * textScale).sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    Switch(
                        checked = !state.isSimulationMode,
                        onCheckedChange = { isLiveGpsChecked ->
                            viewModel.toggleSimulationMode(!isLiveGpsChecked)
                        },
                        modifier = Modifier.testTag("gps_mode_selector_switch")
                    )
                }
            }
        }

        // 4. BIG TICKET FARE OR COMPASS CONTROLLER CARD
        item {
            val fromSt = if (isSinhala) {
                state.activeRoute?.stops?.firstOrNull()?.sinhalaName ?: "ටවුන් හෝල්"
            } else {
                state.activeRoute?.stops?.firstOrNull()?.englishName ?: "Town Hall"
            }
            val toSt = if (isSinhala) {
                state.activeRoute?.stops?.lastOrNull()?.sinhalaName ?: "පිටකොටුව"
            } else {
                state.activeRoute?.stops?.lastOrNull()?.englishName ?: "Pettah"
            }
            val routeNo = state.activeRoute?.routeNumber ?: "138"
            val routeCat = state.activeRoute?.category ?: "AC"

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Two-column Top Row (Journey details on Left, Estimated Fare on Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSinhala) "වත්මන් ගමන" else "CURRENT JOURNEY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                fontSize = (10 * textScale).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$fromSt → $toSt",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = (18 * textScale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val busTypeStr = if (state.busType == "CTB") {
                                if (isSinhala) "ලං.ග.ම. (CTB)" else "CTB (Govt)"
                            } else {
                                if (isSinhala) "පෞද්ගලික (Private)" else "Private"
                            }
                            Text(
                                text = if (isSinhala) {
                                    "මාර්ගය $routeNo • $routeCat • $busTypeStr"
                                } else {
                                    "Route $routeNo • $routeCat • $busTypeStr"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontSize = (12 * textScale).sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isSinhala) "ඇස්තමේන්තු ගාස්තුව" else "Estimated Fare",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = (10 * textScale).sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Rs. ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF001D35),
                                    fontSize = (14 * textScale).sp,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                                Text(
                                    text = String.format("%.0f", state.calculatedFareLkr),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF001D35),
                                    fontSize = (28 * textScale).sp,
                                    modifier = Modifier.testTag("live_ticket_fare_price")
                                )
                                Text(
                                    text = ".00",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    fontSize = (13 * textScale).sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3-Column beautiful Grid for Live Stats: Distance, Speed, Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Stat Cell 1: Distance
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF8F9FF), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isSinhala) "දුර ප්‍රමාණය" else "DISTANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (9 * textScale).sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = LocationUtils.formatDistance(state.distanceTraveledKm),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = (14 * textScale).sp,
                                    modifier = Modifier.testTag("live_distance_covered")
                                )
                            }
                        }

                        // Stat Cell 2: Speed
                        Box(
                            modifier = Modifier
                                .weight(1.5f) // give a tiny bit more room for the km/h suffix
                                .background(Color(0xFFF8F9FF), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isSinhala) "වේගය" else "SPEED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (9 * textScale).sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format("%.1f km/h", state.currentSpeedKmh),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = (14 * textScale).sp,
                                    modifier = Modifier.testTag("live_travel_speed")
                                )
                            }
                        }

                        // Stat Cell 3: Time
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .background(Color(0xFFF8F9FF), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isSinhala) "කාලය" else "TIME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (9 * textScale).sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatTimeElapsed(state.durationSeconds),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = (14 * textScale).sp,
                                    modifier = Modifier.testTag("live_journey_duration")
                                )
                            }
                        }
                    }

                    // Display GPS raw coordinates in a neat small line
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("Lat: %.5f | Lng: %.5f", state.currentLat, state.currentLng),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = (9 * textScale).sp
                            )
                            Text(
                                text = if (state.isSimulationMode) "DEMO GPS" else String.format("Acc: ±%.1fm", state.accuracyMeters),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isSimulationMode) Color(0xFFE11D48) else Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold,
                                fontSize = (9 * textScale).sp
                            )
                        }
                    }
                }
            }
        }

        // Warning panel if users did not grant permissions for Native Location mode
        if (!state.isSimulationMode && !hasPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isSinhala) "GPS දත්ත නොමැත" else "NATIVE LOCATION PERMISSION MISSING",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = (12 * textScale).sp
                        )
                        Text(
                            text = if (isSinhala) {
                                "සැබෑ බස් ගමන් ගාස්තු ස්වයංක්‍රීයව ගණනය කිරීමට GPS අවසර අවශ්‍ය වේ. කරුණාකර 'සවිබල ගන්වන්න' ක්ලික් කරන්න හෝ 'Demo ධාවනය' ක්‍රියාත්මක කරන්න."
                            } else {
                                "The app is set to Live Device GPS tracking, but permissions are denied. Grant location access or switch toggle back to simulation."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = (11 * textScale).sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                if (isSinhala) "ස්ථාන අවසර ලබාදෙන්න" else "Enable Device GPS",
                                fontSize = (11 * textScale).sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // 5. JOURNEY TRIGGER ACTIONS (Start Journey, Stop Journey, Reset buttons)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.status) {
                    JourneyStatus.Idle -> {
                        Button(
                            onClick = { viewModel.startJourney() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001D35), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("start_journey_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start Journey", tint = Color.White)
                                Text(
                                    text = if (isSinhala) "ගමන අරඹන්න (Check-In)" else "START JOURNEY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 * textScale).sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    JourneyStatus.Tracking -> {
                        Button(
                            onClick = { viewModel.stopJourney() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("stop_journey_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop Journey", tint = Color.White)
                                Text(
                                    text = if (isSinhala) "ගමන නවත්වන්න (Check-Out)" else "ARRIVED / CHECK OUT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 * textScale).sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    JourneyStatus.Finished -> {
                        Button(
                            onClick = { viewModel.resetJourney() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001D35), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("reset_journey_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "New Ticket", tint = Color.White)
                                Text(
                                    text = if (isSinhala) "ඊළඟ ගමන සඳහා සූදානම් වන්න" else "CALCULATE NEXT TRIP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 * textScale).sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // COMMUTER ENGAGEMENT HELPER & CHECKLIST PANEL (Shows only during Tracking)
        if (state.status == JourneyStatus.Tracking) {
            item {
                val checklistItemsEn = listOf(
                    "Receive transit ticket from conductor",
                    "Keep your smart card tapped in",
                    "Keep hand luggage safely stored",
                    "Prepare to exit as destination nears"
                )
                val checklistItemsSi = listOf(
                    "කොන්දොස්තර මහතාගෙන් ටිකට් පත ලබාගන්න",
                    "ස්මාර්ට් කාඩ්පත නිසි පරිදි ළඟ තබාගන්න",
                    "අත් බෑගය ආරක්ෂිතව තබාගන්න",
                    "ගමනාන්තය ආසන්න වන විට බැසීමට සූදානම් වන්න"
                )
                
                val checkedStates = remember { mutableStateMapOf<Int, Boolean>() }
                val completedCount = checkedStates.values.count { it }
                val totalChecked = checklistItemsEn.size
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Checklist",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isSinhala) "ගමන් මඟ පිරික්සුම් සහය" else "COMMUTER TRAVEL COMPANION",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontSize = (10 * textScale).sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = if (isSinhala) "ආරක්ෂිත සහ සුවපහසු ගමනක් සඳහා මෙම පියවර අනුගමනය කරන්න." else "Make sure to complete these checklist items for a safe, standard ride.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF78350F),
                            fontSize = (11 * textScale).sp
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Linear Progress Bar for Checklist
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { completedCount.toFloat() / totalChecked },
                                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                                color = Color(0xFFD97706),
                                trackColor = Color(0xFFFEF3C7)
                            )
                            Text(
                                text = "$completedCount/$totalChecked",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                fontSize = 11.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (index in 0 until totalChecked) {
                                val isChecked = checkedStates[index] ?: false
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { checkedStates[index] = !isChecked }
                                        .background(if (isChecked) Color(0xFFFEF3C7) else Color.Transparent)
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFFD97706),
                                            checkmarkColor = Color.White,
                                            uncheckedColor = Color(0xFFFBBF24)
                                        ),
                                        modifier = Modifier.size(32.dp).testTag("checklist_item_$index")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSinhala) checklistItemsSi[index] else checklistItemsEn[index],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isChecked) Color(0xFF78350F) else Color(0xFF451A03),
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = (12 * textScale).sp,
                                        textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. DETAILED PROGRESS STEPS (Shows stops on route, matching check marks if passed)
        if (state.activeRoute != null && state.activeRoute.stops.isNotEmpty()) {
            item {
                Text(
                    text = if (isSinhala) "මාර්ගයේ බස් නැවතුම් පෙළගැස්ම" else "ROUTE BUS STOP SEQUENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = (10 * textScale).sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(state.activeRoute.stops) { stop ->
                val hasPassed = state.stopsPassed.any {
                    it.latitude == stop.latitude && it.longitude == stop.longitude
                }

                val itemBg = if (hasPassed) {
                    Color(0xFFEFF6FF)
                } else {
                    Color.White
                }

                val itemBorderColor = if (hasPassed) {
                    Color(0xFFBFDBFE)
                } else {
                    Color(0xFFE2E8F0)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemBg)
                        .border(
                            1.dp,
                            itemBorderColor,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasPassed) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (hasPassed) {
                                    Icon(Icons.Default.Check, contentDescription = "Passed", tint = Color.White, modifier = Modifier.size(14.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF64748B), CircleShape)
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = if (isSinhala) stop.sinhalaName else stop.englishName,
                                fontWeight = if (hasPassed) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (hasPassed) Color(0xFF001D35) else Color(0xFF64748B),
                                fontSize = (13 * textScale).sp
                            )
                            Text(
                                text = String.format("%.2f km", stop.distanceKmOffset),
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Badge displaying fare for this stage offset
                    Surface(
                        color = if (hasPassed) Color(0xFFD3E3FD) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "LKR ${calculateFareForStop(stop.distanceKmOffset, state.activeRoute).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPassed) Color(0xFF001D35) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = (10 * textScale).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTabScreen(
    journeys: List<JourneyEntity>,
    viewModel: JourneyViewModel,
    isSinhala: Boolean,
    textScale: Float
) {
    val totalDistance = journeys.sumOf { it.distanceKm }
    val totalSpend = journeys.sumOf { it.fareLkr }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SUMMARY TOTALS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFFE2E8F0),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSinhala) "මුළු ගමන් සාරාංශය" else "TOTAL STATS SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = (10 * textScale).sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isSinhala) "මුළු ගමන්" else "Total Trips",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontSize = (11 * textScale).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${journeys.size}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                fontSize = (22 * textScale).sp,
                                modifier = Modifier.testTag("history_total_trips")
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isSinhala) "මුළු දුර" else "Distance Sum",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontSize = (11 * textScale).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1f km", totalDistance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                fontSize = (22 * textScale).sp,
                                modifier = Modifier.testTag("history_total_distance")
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isSinhala) "වියදම" else "Total Fare",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontSize = (11 * textScale).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("Rs %.0f", totalSpend),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2563EB),
                                fontSize = (22 * textScale).sp,
                                modifier = Modifier.testTag("history_total_spend")
                            )
                        }
                    }

                    if (journeys.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isSinhala) "සියලු දත්ත මකන්න" else "CLEAR ALL LOGS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (11 * textScale).sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // DENSE HISTORY CARD ENTRIES
        if (journeys.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.HistoryToggleOff,
                        contentDescription = "No History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isSinhala) {
                            "කිසිදු ගමනක් මෙතෙක් සුරැකී නැත.\nනව ගමනක් ආරම්භ කර අවසන් කරන්න."
                        } else {
                            "No trips recorded yet. Live coordinates of finished sessions will be safely logged."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (13 * textScale).sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Text(
                    text = if (isSinhala) "පසුගිය ගමන් වාර්තා ලේඛනය" else "CONCLUDED JOURNEYS LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = (10 * textScale).sp
                )
            }

            items(journeys) { journey ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Color(0xFFE2E8F0),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFD3E3FD),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = translateCategory(journey.busCategory, isSinhala),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF001D35),
                                            fontSize = (10 * textScale).sp
                                        )
                                    }

                                    // Bus Type (Govt CTB / Private)
                                    val isCtb = journey.busType == "CTB"
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isCtb) Color(0xFFFEE2E2) else Color(0xFFEFF6FF),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isCtb) {
                                                if (isSinhala) "ලං.ග.ම." else "CTB"
                                            } else {
                                                if (isSinhala) "පෞද්ගලික" else "Private"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isCtb) Color(0xFFDC2626) else Color(0xFF2563EB),
                                            fontSize = (9 * textScale).sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = formatDate(journey.timestamp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        fontSize = (10 * textScale).sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TripOrigin,
                                        contentDescription = "Origin",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = journey.fromStation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        fontSize = (13 * textScale).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Drop Point",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = journey.toStation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (13 * textScale).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Rs ${journey.fareLkr.toInt()}",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = (14 * textScale).sp
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteHistoryItem(journey.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Journey",
                                        tint = Color(0xFFDC2626).copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isSinhala) "දුර" else "Dist"}: ${LocationUtils.formatDistance(journey.distanceKm)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = (10 * textScale).sp
                            )
                            Text(
                                text = "${if (isSinhala) "කාලය" else "Duration"}: ${journey.durationMinutes} ${if (isSinhala) "මිනිත්තු" else "min"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = (10 * textScale).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FareGuideTabScreen(
    isSinhala: Boolean,
    textScale: Float
) {
    val officialStages = listOf(
        Pair("1", Pair("0.0 - 2.0 km", "LKR 30.00")),
        Pair("2", Pair("2.1 - 4.5 km", "LKR 42.00")),
        Pair("3", Pair("4.6 - 7.0 km", "LKR 55.00")),
        Pair("4", Pair("7.1 - 10.0 km", "LKR 68.00")),
        Pair("5", Pair("10.1 - 13.0 km", "LKR 84.00")),
        Pair("6", Pair("13.1 - 16.0 km", "LKR 95.00")),
        Pair("7", Pair("16.1 - 20.0 km", "LKR 115.00")),
        Pair("8", Pair("20.1 - 25.0 km", "LKR 140.00")),
        Pair("9", Pair("25.1 - 32.0 km", "LKR 175.00")),
        Pair("10", Pair("32.1 - 40.0 km", "LKR 215.00")),
        Pair("11", Pair("40.1 - 50.0 km", "LKR 260.00")),
        Pair("12", Pair("50.0+ km", "LKR 260 + Rs.9.5 per km"))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // REGULATORY INFORMATION CAPTION
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFFE2E8F0),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (isSinhala) "රාජ්‍ය අනුමත නිල ගාස්තු" else "NTC APPROVED BUS CARD FARE",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF001D35),
                            fontSize = (12 * textScale).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSinhala) {
                                "මෙම ගාස්තු සටහන පොදු ප්‍රවාහන සේවා (CTB/නැගෙනහිර මාර්ග) සාමාන්‍ය සේවා බස් රථ සඳහා වලංගු එකක් වේ. අති සුඛෝපභෝගී හෝ අධිවේගී මාර්ගවල ගාස්තු වෙනස් විය හැක."
                            } else {
                                "This guideline table serves as standard regular CTB services reference to audit transit tickets and protect passengers from conductor over-charging."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontSize = (11 * textScale).sp
                        )
                    }
                }
            }
        }

        item {
            var selectedType by remember { mutableStateOf("Normal") }
            var tripKm by remember { mutableFloatStateOf(10f) }
            
            val base = if (selectedType == "Semi-Luxury") 45.0 else if (selectedType == "AC-Expressway") 120.0 else 30.0
            val rate = if (selectedType == "Semi-Luxury") 14.0 else if (selectedType == "AC-Expressway") 22.0 else 9.5
            
            val fareVal = if (tripKm <= 2.0) {
                base
            } else {
                val excess = tripKm - 2.0
                val raw = base + (excess * rate)
                Math.round(raw * 1.0).toDouble()
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                    .testTag("interactive_fare_calculator_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isSinhala) "ක්‍රියාකාරී ගාස්තු කැල්කියුලේටරය" else "FARE ESTIMATOR INTEGRATOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = (10 * textScale).sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = if (isSinhala) "ඕනෑම දුරක් සඳහා ආසන්න ප්‍රවේශපත්‍ර මිල ගණනය කර බලන්න." else "Check instant regulatory bus fare estimates for any custom Ceylon distance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = (11 * textScale).sp
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Service Type Buttons Segment selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("Normal", "Semi-Luxury", "AC-Expressway").forEach { mode ->
                            val isSel = selectedType == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF001D35) else Color.Transparent)
                                    .clickable { selectedType = mode }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSinhala) translateCategory(mode, true) else mode,
                                    color = if (isSel) Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Slider Row showing distance selected
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSinhala) "ගමන් දුර ප්‍රමාණය: ${Math.round(tripKm).toInt()} km" else "Est. Distance: ${Math.round(tripKm).toInt()} km",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = (12 * textScale).sp
                        )
                        Text(
                            text = "Rate: Rs.${rate}/km",
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                    
                    Slider(
                        value = tripKm,
                        onValueChange = { tripKm = it },
                        valueRange = 1.0f..120.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF9E1C1C),
                            activeTrackColor = Color(0xFF9E1C1C),
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.testTag("calculator_distance_slider")
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Receipt Styled Box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "NTC OFFICIAL TICKETING REFERENCE",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                letterSpacing = 1.sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Ceylon Transport Services Board",
                                fontSize = 10.sp,
                                color = Color(0xFFD97706)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Ticket visual divider
                            Text(
                                text = "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
                                color = Color(0xFFFBBF24),
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${if (isSinhala) translateCategory(selectedType, true) else selectedType} Service",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "Base Fare: LKR ${base.toInt()}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ESTIMATED PRICE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                    Text(
                                        text = "Rs. ${fareVal.toInt()}.00",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp,
                                        color = Color(0xFF78350F),
                                        modifier = Modifier.testTag("calculator_estimated_price")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isSinhala) "සාමාන්‍ය බස් ගාස්තු පියවර සටහන" else "OFFICIAL STAGE DISTANCE CHART",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize = (10 * textScale).sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // GRID TABLE FOR FARES
        items(officialStages) { stage ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color(0xFFE2E8F0),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFD3E3FD),
                                CircleShape
                            )
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stage.first,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF001D35),
                            fontSize = (11 * textScale).sp
                        )
                    }

                    Text(
                        text = "${if (isSinhala) "මාර්ග අදියර " else "Stage "} ${stage.first} • ${stage.second.first}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * textScale).sp
                    )
                }

                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stage.second.second,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = (12 * textScale).sp
                    )
                }
            }
        }

        item {
            val context = LocalContext.current
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .border(
                        1.dp,
                        Color(0xFFFCA5A5),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Megaphone",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isSinhala) "මහජන සහන හා හදිසි ඇමතුම්" else "EMERGENCY CEYLON HELPLINES",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            fontSize = (12 * textScale).sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = if (isSinhala) {
                            "බස් රථ ගමන්වාරයේදී සිදුවන ඕනෑම කරදරයකට, අසාධාරණයකට හෝ හදිසි අවස්ථාවකදී පහත අංක අමතා ක්ෂණික සහය ලබාගන්න."
                        } else {
                            "In case of ticket queries, conductor disputes, lost items, or security concerns, use these official helplines."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F1D1D),
                        fontSize = (11 * textScale).sp
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val helplines = listOf(
                            Triple("1955", if (isSinhala) "ජාතික ප්‍රවාහන කොමිසම (NTC)" else "National Transport Comm. (NTC)", Icons.Default.SupportAgent),
                            Triple("119", if (isSinhala) "පොලිස් හදිසි ඇමතුම් සේවාව" else "Police Emergency Services", Icons.Default.Shield),
                            Triple("0112581120", if (isSinhala) "ලං.ග.ම. සාමාන්‍ය විමසීම් • පැමිණිලි" else "SLTB Main Complaints Branch", Icons.Default.PhoneInTalk)
                        )
                        
                        helplines.forEach { helpline ->
                            Button(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        if (isSinhala) "ඇමතුම සම්බන්ධ කරමින්: ${helpline.first}..." else "Dialing hotline: ${helpline.first} (Simulated)...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF991B1B)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(helpline.third, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = helpline.second,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = helpline.first,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FORMAT TIME HELPER
fun formatTimeElapsed(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

// FORMAT DATE STAMP
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy | hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// OFFLINE FARE STAGE CALCULATOR INDEX
fun calculateFareForStop(distanceKm: Double, route: BusRoute?): Double {
    if (distanceKm <= 0.0) return route?.baseFareLkr ?: 30.0
    val base = route?.baseFareLkr ?: 30.0
    val rate = route?.perKmRateLkr ?: 9.5
    return if (distanceKm <= 2.0) {
        base
    } else {
        val raw = base + ((distanceKm - 2.0) * rate)
        Math.round(raw * 1.0).toDouble()
    }
}

// HELPER TRANSLATION CATEGORIES
fun translateCategory(cat: String, isSinhala: Boolean): String {
    if (!isSinhala) return cat
    return when (cat) {
        "Normal" -> "සාමාන්‍ය (Normal)"
        "Semi-Luxury" -> "අර්ධ සුඛෝපභෝගී (Semi-Luxury)"
        "AC-Expressway" -> "අධිවේගී ඒසී (AC-Expressway)"
        else -> "වෙනත් (Custom)"
    }
}
