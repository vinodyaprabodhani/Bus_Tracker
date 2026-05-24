package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BusRoute
import com.example.model.RouteStop
import com.example.util.LocationUtils

@Composable
fun OfflineRouteMap(
    activeRoute: BusRoute?,
    currentLat: Double,
    currentLng: Double,
    isSinhala: Boolean,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableStateOf(1.0f) }
    var panX by remember { mutableStateOf(0.0f) }
    var panY by remember { mutableStateOf(0.0f) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Infinite breathing animation for the live GPS indicator
    val infiniteTransition = rememberInfiniteTransition(label = "GPSBeacon")
    val beaconRadiusPulse by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadiusPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        if (activeRoute != null && activeRoute.stops.isNotEmpty()) {
            val stops = activeRoute.stops

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Find boundaries
                val minLat = stops.minOf { it.latitude }
                val maxLat = stops.maxOf { it.latitude }
                val minLng = stops.minOf { it.longitude }
                val maxLng = stops.maxOf { it.longitude }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

                val width = size.width
                val height = size.height

                // Utility mapper to translate coordinates to canvas bounds
                fun mapCoords(lat: Double, lng: Double): Offset {
                    // Normalize standard coordinates: Lng is X, Lat is Y (inverted screen coordinates)
                    val xFraction = (lng - minLng) / lngSpan
                    val yFraction = 1.0 - ((lat - minLat) / latSpan)

                    val centerX = width / 2.0f
                    val centerY = height / 2.0f

                    // Apply coordinate scaling & user zoom / pan offsets
                    val scalarX = (xFraction.toFloat() - 0.5f) * (width * 0.72f) * zoomScale + centerX + panX
                    val scalarY = (yFraction.toFloat() - 0.5f) * (height * 0.72f) * zoomScale + centerY + panY

                    return Offset(scalarX, scalarY)
                }

                // 1. Draw geographic background grid to make it look like an offline navigational system
                val gridLinesCount = 8
                val gridColor = Color.Gray.copy(alpha = 0.15f)
                for (i in 0..gridLinesCount) {
                    val gridX = (width / gridLinesCount) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(gridX, 0f),
                        end = Offset(gridX, height),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    val gridY = (height / gridLinesCount) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gridY),
                        end = Offset(width, gridY),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // 2. Draw route path connecting nodes sequential lines
                val routePoints = stops.map { mapCoords(it.latitude, it.longitude) }
                for (i in 0 until routePoints.size - 1) {
                    drawLine(
                        color = Color(0xFF0D9488), // Sri Lankan Green Accent
                        start = routePoints[i],
                        end = routePoints[i + 1],
                        strokeWidth = 8f,
                        pathEffect = PathEffect.cornerPathEffect(15f)
                    )
                }

                // 3. Draw stops / nodes
                stops.forEachIndexed { index, stop ->
                    val point = mapCoords(stop.latitude, stop.longitude)

                    val isTerminus = index == 0 || index == stops.size - 1
                    val nodeColor = if (isTerminus) Color(0xFF1E293B) else Color(0xFF0F766E)
                    val nodeRadius = if (isTerminus) 9f else 6f

                    // Draw stop anchor circle
                    drawCircle(
                        color = nodeColor,
                        radius = nodeRadius,
                        center = point
                    )

                    // Draw stop anchor border ring
                    drawCircle(
                        color = Color.White,
                        radius = nodeRadius + 3f,
                        center = point,
                        style = Stroke(width = 3f)
                    )

                    // Draw stop names in english or sinhala locale dynamically
                    val stopLabel = if (isSinhala) stop.sinhalaName else stop.englishName
                    val textLayoutResult = textMeasurer.measure(
                        text = stopLabel,
                        style = TextStyle(
                            color = Color(0xFF1E293B),
                            fontSize = 10.sp,
                            background = Color.White.copy(alpha = 0.78f)
                        )
                    )

                    // Offset text tag slightly so it doesn't overlap the node ring
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(point.x + 12f, point.y - 18f)
                    )
                }

                // 4. Draw passenger current coordinates
                val passengerPoint = mapCoords(currentLat, currentLng)

                // Render pulsing location halo
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.28f),
                    radius = beaconRadiusPulse,
                    center = passengerPoint
                )

                // Render outer coordinate ring icon
                drawCircle(
                    color = Color.White,
                    radius = 9f,
                    center = passengerPoint
                )

                // Render solid central passenger GPS dot
                drawCircle(
                    color = Color(0xFF2563EB),
                    radius = 6f,
                    center = passengerPoint
                )
            }
        } else {
            // Emptystate placeholder design
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "No Route",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isSinhala) "මාර්ග දත්ත නොමැත" else "Offline Maps Coordinates Unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. Precise On-Map Overlay Controllers for Zoom In & Zoom Out
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.5f) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
            
            Text(
                text = "${(zoomScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }
        }

        // Geographic Badge: Displays offline status of the map rendering coordinates
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF22C55E), CircleShape)
                )
                Text(
                    text = if (isSinhala) "සබැඳි නොවන සිතියම (Offline)" else "OFFLINE GEOPATH MAP",
                    color = Color.White,
                    fontSize = 8.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
