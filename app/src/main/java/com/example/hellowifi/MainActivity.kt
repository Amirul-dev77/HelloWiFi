package com.example.hellowifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hellowifi.ui.theme.HelloWiFiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        requestPermissionsIfNeeded()

        setContent {
            HelloWiFiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WifiScannerApp(wifiManager)
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            // Note: Even with NEARBY_WIFI_DEVICES, many devices still require Fine Location for startScan()
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions, PERMISSION_REQUEST_CODE)
        }
    }
}

@Composable
fun WifiScannerApp(wifiManager: WifiManager) {
    var scanResults by remember { mutableStateOf<List<ScanResultDisplay>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "📡 Wi-Fi Scanner",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                scope.launch {
                    isScanning = true
                    // Perform scan on IO thread to avoid blocking UI
                    val results = withContext(Dispatchers.IO) {
                        performWiFiScan(wifiManager, context)
                    }
                    scanResults = results
                    isScanning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Scan")
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "Scanning..." else "Start Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🔍 Top 4 Strongest APs (${scanResults.size}/4 shown)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (scanResults.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scan results. Ensure Wi-Fi and Location are ON.")
            }
        } else if (isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scanResults) { ap -> APCard(ap) }
            }
        }
    }
}

@Composable
fun APCard(ap: ScanResultDisplay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "📶 ${ap.ssid}", style = MaterialTheme.typography.titleMedium)
                SignalStrengthIndicator(ap.signalStrength)
            }
            Text(text = "🔹 BSSID: ${ap.bssid}", style = MaterialTheme.typography.bodySmall)
            Text(text = "📊 Signal: ${ap.signalStrength} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SignalStrengthIndicator(dbm: Int) {
    val (strength, color) = when {
        dbm > -50 -> "Excellent" to Color(0xFF4CAF50)
        dbm > -60 -> "Good" to Color(0xFF8BC34A)
        dbm > -70 -> "Fair" to Color(0xFFFFC107)
        else -> "Poor" to Color(0xFFF44336)
    }
    Text(text = strength, color = color, style = MaterialTheme.typography.labelMedium)
}

data class ScanResultDisplay(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val capabilities: String
)

private suspend fun performWiFiScan(wifiManager: WifiManager, context: Context): List<ScanResultDisplay> {
    return try {
        // 1. Check if Wi-Fi is enabled (Cannot enable programmatically anymore)
        if (!wifiManager.isWifiEnabled) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please turn ON Wi-Fi manually", Toast.LENGTH_LONG).show()
            }
            return emptyList()
        }

        // 2. Check if Location is enabled (Required for Wi-Fi scanning)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        if (!isLocationEnabled) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please turn ON Location Services", Toast.LENGTH_LONG).show()
            }
            return emptyList()
        }

        // 3. Start scan (Subject to Throttling: 4 times / 2 mins)
        val scanSuccess = wifiManager.startScan()
        if (!scanSuccess) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Scan throttled or rejected by system", Toast.LENGTH_SHORT).show()
            }
            // Return existing results even if new scan didn't start
            return formatResults(wifiManager.scanResults)
        }

        // 4. Wait for scan to complete (3 seconds) - non-blocking delay
        delay(3000)

        formatResults(wifiManager.scanResults)

    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private fun formatResults(results: List<ScanResult>): List<ScanResultDisplay> {
    return results
        .filter { it.SSID.isNotEmpty() }
        .groupBy { it.SSID }
        .mapNotNull { (_, list) -> list.maxByOrNull { it.level } }
        .sortedByDescending { it.level }
        .take(4)
        .map { 
            ScanResultDisplay(
                ssid = it.SSID,
                bssid = it.BSSID,
                signalStrength = it.level,
                capabilities = it.capabilities
            )
        }
}
