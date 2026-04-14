package com.example.hellowifi // Replace with your actual package name

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager

    // Permission launcher to handle the user's response to the location permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Location permission is required to scan Wi-Fi", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WifiManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Request location permission on startup if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WifiScannerScreen(wifiManager)
                }
            }
        }
    }
}

@Composable
fun WifiScannerScreen(wifiManager: WifiManager) {
    val context = LocalContext.current

    // Explicit state management for the scan results
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Register a BroadcastReceiver to listen for when the Wi-Fi scan finishes
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    // Suppressing deprecation because the assignment explicitly requires getScanResults()
                    @Suppress("DEPRECATION")
                    scanResults = wifiManager.scanResults
                }
                isScanning = false
            }
        }
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, intentFilter)

        // Cleanup receiver when the composable leaves the composition
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // SCAN Button
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    isScanning = true
                    @Suppress("DEPRECATION")
                    wifiManager.startScan() // Assignment requirement
                } else {
                    Toast.makeText(context, "Location permission missing", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp),
            enabled = !isScanning
        ) {
            Text(if (isScanning) "Scanning..." else "Scan for Wi-Fi")
        }

        Text(
            text = "Results",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider()

        // List View of Results
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(scanResults) { index, result ->
                WifiResultItem(index = index + 1, result = result)
                Divider()
            }
        }
    }
}

@Composable
fun WifiResultItem(index: Int, result: ScanResult) {
    // Extracting the exact data points requested by Task 4.1
    val essid = result.SSID.ifEmpty { "<Hidden Network>" }
    val bssid = result.BSSID
    val signalStrength = result.level
    val encryption = if (result.capabilities.contains("WEP") ||
        result.capabilities.contains("WPA") ||
        result.capabilities.contains("EAP")) {
        "Encrypted (${result.capabilities})"
    } else {
        "Open"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Formatting to match the image style: "1. SSID--Signal"
        Text(
            text = "$index. $essid -- $signalStrength",
            fontWeight = FontWeight.Bold
        )
        // Additional details requested by the assignment
        Text(
            text = "BSSID: $bssid",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Security: $encryption",
            style = MaterialTheme.typography.bodySmall
        )
    }
}