package com.example.hellowifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE)
        permissions.add(Manifest.permission.CHANGE_NETWORK_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
    var showTop4 by remember { mutableStateOf(false) }

    var selectedAp by remember { mutableStateOf<ScanResultDisplay?>(null) }
    var connectedMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

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
                    val results = withContext(Dispatchers.IO) {
                        performWiFiScan(wifiManager, context)
                    }
                    scanResults = results
                    isScanning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning && !isConnecting
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Scan")
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "Scanning..." else "SCAN")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { showTop4 = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showTop4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Show All APs")
            }

            Button(
                onClick = { showTop4 = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showTop4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Top 4 Strongest")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayResults = if (showTop4) {
            scanResults
                .groupBy { it.ssid }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.signalStrength } }
                .sortedByDescending { it.signalStrength }
                .take(4)
        } else {
            scanResults
        }

        Text(
            text = if (showTop4) "🏆 Top 4 Strongest APs" else "📋 All Available APs (${displayResults.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (scanResults.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scan results. Ensure Wi-Fi and Location are ON.")
            }
        } else if (isScanning || isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isConnecting) "Connecting..." else "Scanning...")
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayResults) { ap ->
                    APCard(ap, onClick = { selectedAp = it })
                }
            }
        }
    }

    selectedAp?.let { ap ->
        val isEap = ap.capabilities.contains("EAP", ignoreCase = true)
        val isSecure = ap.capabilities.contains("WPA", ignoreCase = true) ||
                ap.capabilities.contains("WEP", ignoreCase = true) ||
                ap.capabilities.contains("RSN", ignoreCase = true)

        if (isEap) {
            EapAuthDialog(
                ssid = ap.ssid,
                onDismiss = { selectedAp = null },
                onConnect = { username, password ->
                    selectedAp = null
                    isConnecting = true
                    connectToWifi(context, wifiManager, ap.ssid, username, password, ap.capabilities) { success, result ->
                        isConnecting = false
                        if (success) {
                            connectedMessage = "Successfully connected to:\nESSID: ${ap.ssid}\nIP Address: $result"
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        } else if (isSecure) {
            PskAuthDialog(
                ssid = ap.ssid,
                onDismiss = { selectedAp = null },
                onConnect = { password ->
                    selectedAp = null
                    isConnecting = true
                    connectToWifi(context, wifiManager, ap.ssid, null, password, ap.capabilities) { success, result ->
                        isConnecting = false
                        if (success) {
                            connectedMessage = "Successfully connected to:\nESSID: ${ap.ssid}\nIP Address: $result"
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        } else {
            LaunchedEffect(ap) {
                selectedAp = null
                isConnecting = true
                connectToWifi(context, wifiManager, ap.ssid, null, null, ap.capabilities) { success, result ->
                    isConnecting = false
                    if (success) {
                        connectedMessage = "Successfully connected to:\nESSID: ${ap.ssid}\nIP Address: $result"
                    } else {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    connectedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { connectedMessage = null },
            confirmButton = {
                TextButton(onClick = { connectedMessage = null }) { Text("OK") }
            },
            title = { Text("Connection Status") },
            text = { Text(msg) }
        )
    }
}

@Composable
fun PskAuthDialog(ssid: String, onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to $ssid") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            Button(onClick = { onConnect(password) }) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EapAuthDialog(ssid: String, onDismiss: () -> Unit, onConnect: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to $ssid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enterprise Authentication Required", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(username, password) }) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun APCard(ap: ScanResultDisplay, onClick: (ScanResultDisplay) -> Unit) {
    val isEncrypted = ap.capabilities.contains("WEP", ignoreCase = true) ||
            ap.capabilities.contains("WPA", ignoreCase = true) ||
            ap.capabilities.contains("RSN", ignoreCase = true) ||
            ap.capabilities.contains("EAP", ignoreCase = true)

    val encryptionText = if (isEncrypted) "Encrypted" else "Open"
    val encryptionColor = if (isEncrypted) Color(0xFFF44336) else Color(0xFF4CAF50)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(ap) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "📶 ${ap.ssid}", style = MaterialTheme.typography.titleMedium)
                SignalStrengthIndicator(ap.signalStrength)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "📍 BSSID: ${ap.bssid}", style = MaterialTheme.typography.bodySmall)
            Text(text = "📊 Signal: ${ap.signalStrength} dBm", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "🔒 Mode: $encryptionText",
                    style = MaterialTheme.typography.bodySmall,
                    color = encryptionColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ap.capabilities,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
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

@SuppressLint("MissingPermission")
private suspend fun performWiFiScan(wifiManager: WifiManager, context: Context): List<ScanResultDisplay> {
    return try {
        if (!wifiManager.isWifiEnabled) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please turn ON Wi-Fi manually", Toast.LENGTH_LONG).show()
            }
            return emptyList()
        }

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

        val scanSuccess = wifiManager.startScan()
        if (!scanSuccess) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Scan throttled or rejected by system", Toast.LENGTH_SHORT).show()
            }
            return formatResults(wifiManager.scanResults)
        }

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
        .sortedByDescending { it.level }
        .map {
            ScanResultDisplay(
                ssid = it.SSID,
                bssid = it.BSSID,
                signalStrength = it.level,
                capabilities = it.capabilities
            )
        }
}

@SuppressLint("MissingPermission")
private fun connectToWifi(
    context: Context,
    wifiManager: WifiManager,
    ssid: String,
    user: String?,
    pass: String?,
    capabilities: String,
    onResult: (Boolean, String) -> Unit // Return true/false and a message
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)

            if (capabilities.contains("EAP", ignoreCase = true)) {
                if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
                    onResult(false, "Username and Password cannot be empty.")
                    return
                }
                val enterpriseConfig = WifiEnterpriseConfig().apply {
                    identity = user
                    password = pass
                    eapMethod = WifiEnterpriseConfig.Eap.PEAP
                    phase2Method = WifiEnterpriseConfig.Phase2.MSCHAPV2
                }
                specifierBuilder.setWpa2EnterpriseConfig(enterpriseConfig)

            } else if (capabilities.contains("WPA", ignoreCase = true) ||
                capabilities.contains("WEP", ignoreCase = true) ||
                capabilities.contains("RSN", ignoreCase = true)) {

                if (pass.isNullOrEmpty() || pass.length < 8) {
                    onResult(false, "Password must be at least 8 characters.")
                    return
                }
                specifierBuilder.setWpa2Passphrase(pass)
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    val ipAddress = linkProperties?.linkAddresses?.firstOrNull {
                        it.address is java.net.Inet4Address
                    }?.address?.hostAddress ?: "Unknown IP"

                    connectivityManager.unregisterNetworkCallback(this)
                    Handler(Looper.getMainLooper()).post { onResult(true, ipAddress) }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    // Handles the user hitting "Cancel" on the system prompt
                    Handler(Looper.getMainLooper()).post { onResult(false, "Connection cancelled or failed.") }
                }
            }
            connectivityManager.requestNetwork(request, networkCallback)

        } else {
            // Legacy approach for Android 9 and below
            val conf = WifiConfiguration()
            conf.SSID = "\"$ssid\""

            if (capabilities.contains("EAP", ignoreCase = true)) {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
                val enterpriseConfig = WifiEnterpriseConfig()
                enterpriseConfig.identity = user
                enterpriseConfig.password = pass
                enterpriseConfig.eapMethod = WifiEnterpriseConfig.Eap.PEAP
                conf.enterpriseConfig = enterpriseConfig
            } else if (capabilities.contains("WPA", ignoreCase = true) || capabilities.contains("RSN", ignoreCase = true)) {
                conf.preSharedKey = "\"$pass\""
            } else if (capabilities.contains("WEP", ignoreCase = true)) {
                conf.wepKeys[0] = "\"$pass\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            } else {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            val netId = wifiManager.addNetwork(conf)
            if (netId == -1) {
                onResult(false, "Failed to add network configuration.")
                return
            }
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()

            Handler(Looper.getMainLooper()).postDelayed({
                @Suppress("DEPRECATION")
                val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                if (ipAddress == "0.0.0.0") {
                    onResult(false, "Failed to obtain IP address. Check password.")
                } else {
                    onResult(true, ipAddress)
                }
            }, 5000)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // If an exception occurs, we catch it and display a Toast instead of crashing
        Handler(Looper.getMainLooper()).post {
            onResult(false, "Error: ${e.localizedMessage}")
        }
    }
}