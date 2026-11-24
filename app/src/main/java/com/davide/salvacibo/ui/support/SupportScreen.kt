package com.davide.salvacibo.ui.support

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davide.salvacibo.AppConfig
import com.davide.salvacibo.BuildConfig
import com.davide.salvacibo.monetization.BillingManager
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.findActivity

/** "Supporta e info": offrimi un caffè (toglie le pubblicità), donazioni, condividi, crediti e privacy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val billing by vm.billing.collectAsStateWithLifecycle()
    val privacyOptions by vm.privacyOptionsRequired.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.restorePurchases() }
    LaunchedEffect(billing.message) {
        billing.message?.let {
            snackbar.showSnackbar(it)
            vm.billingMessageShown()
        }
    }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Supporta e info") },
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
            Hero(isSupporter = billing.isSupporter)

            if (BuildConfig.BILLING_ENABLED) {
                Card {
                    Text("Offrimi qualcosa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pagamento unico tramite Google Play, niente abbonamenti. " +
                            "Qualsiasi scelta toglie le pubblicità per sempre, anche cambiando telefono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    billing.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProductRow(
                            item = item,
                            loading = !billing.ready,
                            busy = billing.purchasing,
                            onBuy = { context.findActivity()?.let { vm.buy(it, item.product.id) } },
                        )
                    }
                    if (!billing.ready) {
                        Text(
                            "Sto contattando il Play Store… Se i prezzi non compaiono, controlla la connessione.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    TextButton(onClick = { vm.restorePurchases() }) { Text("Ripristina acquisti") }
                }
            }

            Card {
                Text("Aiutami a farla conoscere", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Una recensione o un consiglio a un amico valgono quanto un caffè.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                )
                            } catch (_: ActivityNotFoundException) {
                                open(AppConfig.playStoreUrl(context.packageName))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Valuta")
                    }
                    OutlinedButton(
                        onClick = {
                            val text = "Uso Salvacibo per non buttare più il cibo scaduto: scansioni il prodotto " +
                                "e ti avvisa prima della scadenza. " + AppConfig.playStoreUrl(context.packageName)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, "Consiglia Salvacibo"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Consiglia")
                    }
                }
            }

            Card {
                Text("Info", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                InfoLine("Versione", BuildConfig.VERSION_NAME)
                Text(
                    "Nomi, marche e foto dei prodotti vengono da Open Food Facts, database libero e collaborativo " +
                        "(dati con licenza ODbL, immagini CC BY-SA).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                TextButton(onClick = { open("https://it.openfoodfacts.org") }) { Text("Visita Open Food Facts") }
                if (AppConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                    TextButton(onClick = { open(AppConfig.PRIVACY_POLICY_URL) }) { Text("Informativa privacy") }
                }
                if (BuildConfig.ADS_ENABLED && privacyOptions) {
                    TextButton(onClick = { context.findActivity()?.let { vm.showPrivacyOptions(it) } }) {
                        Text("Gestisci consenso pubblicità")
                    }
                }
                if (AppConfig.SUPPORT_EMAIL.isNotBlank()) {
                    TextButton(onClick = { open("mailto:${AppConfig.SUPPORT_EMAIL}") }) {
                        Text("Scrivimi: ${AppConfig.SUPPORT_EMAIL}")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Hero(isSupporter: Boolean) {
    val gradient = Brush.linearGradient(listOf(Color(0xFFFF7A59), Color(0xFFFFB547)))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(gradient)
            .padding(22.dp)
    ) {
        Column {
            Text(if (isSupporter) "💚" else "☕", fontSize = 40.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isSupporter) "Grazie, sei un sostenitore!" else "Salvacibo è gratis",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSupporter) {
                    "Niente più pubblicità, per sempre. Il tuo aiuto mi permette di continuare a migliorarla."
                } else {
                    "Tutte le funzioni sono gratuite e lo resteranno. Il piccolo banner in fondo aiuta a coprire i costi: " +
                        "se l'app ti è utile, offrimi un caffè e sparisce per sempre."
                },
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProductRow(item: BillingManager.Item, loading: Boolean, busy: Boolean, onBuy: () -> Unit) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(item.product.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.product.title, style = MaterialTheme.typography.titleMedium)
            Text(
                item.product.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            item.owned -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" Grazie!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            item.price == null && loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            item.price == null -> Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> FilledTonalButton(onClick = onBuy, enabled = !busy, modifier = Modifier.widthIn(min = 88.dp)) {
                Text(item.price, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}
