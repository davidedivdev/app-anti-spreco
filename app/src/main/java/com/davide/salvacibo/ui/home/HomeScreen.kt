package com.davide.salvacibo.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davide.salvacibo.BuildConfig
import com.davide.salvacibo.SalvaciboApp
import com.davide.salvacibo.data.Product
import com.davide.salvacibo.data.categoryEnum
import com.davide.salvacibo.data.expiryInfo
import com.davide.salvacibo.data.isOpened
import com.davide.salvacibo.data.type
import com.davide.salvacibo.domain.ExpiryCalc
import com.davide.salvacibo.domain.ExpiryInfo
import com.davide.salvacibo.domain.ExpiryType
import com.davide.salvacibo.domain.ProductStatus
import com.davide.salvacibo.domain.Urgency
import com.davide.salvacibo.monetization.BannerAd
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.DaysPill
import com.davide.salvacibo.ui.components.ProductAvatar
import com.davide.salvacibo.ui.components.Tag
import com.davide.salvacibo.ui.components.findActivity
import com.davide.salvacibo.ui.theme.LocalUrgencyColors
import java.time.LocalDate

private enum class Filter(val label: String) { ALL("Tutti"), SOON("In scadenza"), OPENED("Aperti") }

private data class ProductRow(val product: Product, val info: ExpiryInfo)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onScan: () -> Unit,
    onAddManual: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onSettings: () -> Unit,
    onSupport: () -> Unit,
) {
    AskNotificationPermissionOnce()
    val context = LocalContext.current

    val products by vm.products.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val consumed by vm.consumedThisMonth.collectAsStateWithLifecycle()
    val thrown by vm.thrownThisMonth.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val billing by vm.billing.collectAsStateWithLifecycle()
    val adsAllowed by vm.adsAllowed.collectAsStateWithLifecycle()
    val showAds = BuildConfig.ADS_ENABLED && adsAllowed && !billing.isSupporter
    val snackbar = remember { SnackbarHostState() }

    // "Pollo: consumato 🎉  [Annulla]"
    val finished = vm.lastFinished
    LaunchedEffect(finished) {
        if (finished != null) {
            val text = if (finished.status == ProductStatus.CONSUMED) {
                "${finished.product.name}: consumato 🎉"
            } else {
                "${finished.product.name}: buttato"
            }
            val result = snackbar.showSnackbar(text, actionLabel = "Annulla", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoFinish(finished) else vm.clearFinished()
        }
    }

    // dopo qualche prodotto salvato chiede (una volta sola) una recensione sul Play Store
    LaunchedEffect(consumed) {
        context.findActivity()?.let { vm.askReview(it, consumed) }
    }

    val today = LocalDate.now()
    val rows = remember(products, settings, today) {
        products.map { ProductRow(it, it.expiryInfo(settings, today)) }.sortedBy { it.info.effectiveExpiry }
    }
    val urgentCount = rows.count { it.info.urgency != Urgency.OK }
    val visible = when (filter) {
        Filter.ALL -> rows
        Filter.SOON -> rows.filter { it.info.urgency != Urgency.OK }
        Filter.OPENED -> rows.filter { it.product.isOpened }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showAds) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BannerAd(Modifier.navigationBarsPadding().padding(top = 8.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                text = { Text("Scansiona") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Header(onAddManual = onAddManual, onSettings = onSettings, onSupport = onSupport)
            }
            item(key = "summary") {
                SummaryCard(
                    total = rows.size,
                    urgent = urgentCount,
                    consumed = consumed,
                    thrown = thrown,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (rows.isNotEmpty()) {
                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Filter.entries) { f ->
                            val count = when (f) {
                                Filter.ALL -> rows.size
                                Filter.SOON -> urgentCount
                                Filter.OPENED -> rows.count { it.product.isOpened }
                            }
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text("${f.label}  $count") },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") { EmptyState(onScan) }
            } else if (visible.isEmpty()) {
                item(key = "empty-filter") {
                    Text(
                        "Niente qui, ottimo! 🎉",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            } else {
                val groups = visible.groupBy { sectionOf(it.info.daysLeft) }
                for ((section, sectionRows) in groups) {
                    item(key = "section-$section") {
                        Text(
                            section,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
                                .animateItem(),
                        )
                    }
                    items(sectionRows, key = { it.product.id }) { row ->
                        ProductCard(
                            product = row.product,
                            info = row.info,
                            onClick = { onOpenProduct(row.product.id) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private fun sectionOf(daysLeft: Long): String = when {
    daysLeft < 0 -> "Scaduti"
    daysLeft == 0L -> "Oggi"
    daysLeft <= 3 -> "Nei prossimi giorni"
    daysLeft <= 7 -> "Questa settimana"
    daysLeft <= 31 -> "Questo mese"
    else -> "Più avanti"
}

@Composable
private fun Header(onAddManual: () -> Unit, onSettings: () -> Unit, onSupport: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Salvacibo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Il tuo frigo", style = MaterialTheme.typography.headlineLarge)
        }
        IconButton(onClick = onSupport) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Supporta Salvacibo",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        IconButton(onClick = onAddManual) {
            Icon(Icons.Filled.Add, contentDescription = "Aggiungi a mano")
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
        }
    }
}

@Composable
private fun SummaryCard(total: Int, urgent: Int, consumed: Int, thrown: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(
        listOf(scheme.primary, Color(0xFF2FB27A), Color(0xFF8BD3A8)),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(gradient)
            .padding(20.dp),
    ) {
        Column {
            val headline = when {
                total == 0 -> "Pronto a salvare il cibo?"
                urgent == 0 -> "Tutto sotto controllo ✨"
                urgent == 1 -> "1 prodotto da consumare presto"
                else -> "$urgent prodotti da consumare presto"
            }
            Text(headline, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (total == 1) "1 prodotto nel frigo e in dispensa" else "$total prodotti nel frigo e in dispensa",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBubble("$consumed", "salvati questo mese", Modifier.weight(1f))
                StatBubble("$thrown", "buttati questo mese", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatBubble(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ProductCard(product: Product, info: ExpiryInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val urgencyColors = LocalUrgencyColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductAvatar(category = product.categoryEnum, urgency = info.urgency, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(product.brand, product.categoryEnum.label).joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (product.isOpened) {
                        Tag(
                            "Aperto",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            background = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                    if (product.type == ExpiryType.BEST_BEFORE && !info.limitedByOpening) {
                        Tag(
                            "TMC",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    Text(
                        ExpiryCalc.statusLine(info, product.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = urgencyColors.strong(info.urgency),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            DaysPill(info.daysLeft, info.urgency)
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onScan)
                .padding(28.dp),
        ) {
            Text("🥗", fontSize = 56.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Il frigo è vuoto", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Inquadra il codice a barre e poi la data di scadenza: al resto penso io, " +
                "e ti avviso qualche giorno prima che scada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Su Android 13+ chiede il permesso per le notifiche la prima volta che si apre l'app. */
@Composable
private fun AskNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val prefs = (context.applicationContext as SalvaciboApp).prefs
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!prefs.askedNotificationPermission) {
            prefs.askedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
