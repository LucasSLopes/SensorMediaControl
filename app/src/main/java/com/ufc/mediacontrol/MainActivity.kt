package com.ufc.mediacontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ufc.mediacontrol.service.GestureForegroundService
import com.ufc.mediacontrol.service.MediaNotificationListener

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var hasNotificationPermission by remember {
        mutableStateOf(
            !needsNotificationPermission || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isServiceActive by remember { mutableStateOf(false) }
    var neutralZone by remember { mutableStateOf(20f) }
    var tiltThreshold by remember { mutableStateOf(35f) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        Toast.makeText(
            context,
            if (granted) "Notificações permitidas" else "Permissão negada",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        if (needsNotificationPermission && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MediaControl",
            style = MaterialTheme.typography.headlineMedium
        )

        val hasNotificationAccess = MediaNotificationListener.getController() != null
        if (!hasNotificationAccess) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("⚠️ Habilitar acesso a notificações")
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Controle de gestos", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isServiceActive,
                onCheckedChange = { enable ->
                    if (enable) {
                        if (!hasNotificationPermission && needsNotificationPermission) {
                            Toast.makeText(context, "Permita notificações primeiro", Toast.LENGTH_SHORT).show()
                            return@Switch
                        }

                        val intent = Intent(context, GestureForegroundService::class.java).apply {
                            action = GestureForegroundService.ACTION_START
                            putExtra("neutralZoneDeg", neutralZone)
                            putExtra("tiltThresholdDeg", tiltThreshold)
                        }
                        ContextCompat.startForegroundService(context, intent)
                        isServiceActive = true
                        Toast.makeText(context, "Controle ativado", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(context, GestureForegroundService::class.java).apply {
                            action = GestureForegroundService.ACTION_STOP
                        }
                        context.startService(intent)
                        isServiceActive = false
                        Toast.makeText(context, "Controle desativado", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        HorizontalDivider()

        Text("Configurações", style = MaterialTheme.typography.titleMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zona neutra: ${neutralZone.toInt()}°")
            Slider(
                value = neutralZone,
                onValueChange = { neutralZone = it },
                valueRange = 10f..30f,
                steps = 19,
                enabled = !isServiceActive
            )

            Text("Ângulo de inclinação: ${tiltThreshold.toInt()}°")
            Slider(
                value = tiltThreshold,
                onValueChange = { tiltThreshold = it },
                valueRange = 25f..50f,
                steps = 24,
                enabled = !isServiceActive
            )

            if (isServiceActive) {
                Text(
                    text = "Desative o controle para ajustar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
