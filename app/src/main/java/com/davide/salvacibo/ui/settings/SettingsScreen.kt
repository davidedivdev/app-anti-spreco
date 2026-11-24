package com.davide.salvacibo.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davide.salvacibo.notifications.Notifier
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.Stepper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onSupport: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(Notifier.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = Notifier.canNotify(context)
    }
    // valore "live" dello slider, salvato quando lo rilasci
    var hourSlider by remember(s.notifyHour) { mutableFloatStateOf(s.notifyHour.toFloat()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!canNotify) {
                Card(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Le notifiche sono disattivate",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Senza permesso non posso avvisarti delle scadenze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }) { Text("Attiva le notifiche") }
                }
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avvisi di scadenza", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Una notifica al giorno per ogni prodotto in scadenza",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.notificationsEnabled,
                        onCheckedChange = { on -> vm.updateSettings { it.copy(notificationsEnabled = on) } },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Ora dell'avviso: alle ${hourSlider.roundToInt()}:00", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = hourSlider,
                    onValueChange = { hourSlider = it },
                    onValueChangeFinished = {
                        val h = hourSlider.roundToInt()
                        vm.updateSettings { it.copy(notifyHour = h) }
                    },
                    valueRange = 6f..22f,
                    steps = 15,
                    enabled = s.notificationsEnabled,
                )
            }

            Card {
                Text("Quanti giorni prima avvisarmi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Es. pollo che scade il 22 con 2 giorni: avvisi il 20, il 21 e il 22.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WarnRow(
                    title = "Da consumarsi entro",
                    subtitle = "carne, pesce, latte, freschi",
                    value = s.warnDaysUseBy,
                    onChange = { v -> vm.updateSettings { it.copy(warnDaysUseBy = v) } },
                )
                WarnRow(
                    title = "Preferibilmente entro",
                    subtitle = "pasta, biscotti, conserve",
                    value = s.warnDaysBestBefore,
                    onChange = { v -> vm.updateSettings { it.copy(warnDaysBestBefore = v) } },
                )
                WarnRow(
                    title = "Prodotti aperti",
                    subtitle = "marmellata, sughi, formaggi aperti…",
                    value = s.warnDaysOpened,
                    onChange = { v -> vm.updateSettings { it.copy(warnDaysOpened = v) } },
                )
            }

            FilledTonalButton(
                onClick = { vm.sendTestNotification() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Text("  Prova le notifiche adesso")
            }

            Surface(
                onClick = onSupport,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "☕  Supporta Salvacibo e info",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "Togli le pubblicità, crediti, privacy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Card {
                Text("Come funziona", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Il codice a barre e la data vengono letti sul telefono, senza inviare foto.\n" +
                        "• Nome e immagine arrivano da Open Food Facts, un database libero; " +
                        "i prodotti che hai già comprato li ricordo io.\n" +
                        "• Quando apri un prodotto, la data che conta diventa la più vicina tra la scadenza " +
                        "stampata e il conto alla rovescia dell'apertura.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WarnRow(title: String, subtitle: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Stepper(value = value, onValueChange = onChange, range = 0..14)
    }
}

@Composable
private fun Card(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}
