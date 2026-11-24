package com.davide.salvacibo.ui.detail

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davide.salvacibo.data.Product
import com.davide.salvacibo.data.categoryEnum
import com.davide.salvacibo.data.expiryDate
import com.davide.salvacibo.data.expiryInfo
import com.davide.salvacibo.data.openedDate
import com.davide.salvacibo.data.type
import com.davide.salvacibo.domain.ExpiryCalc
import com.davide.salvacibo.domain.ExpiryInfo
import com.davide.salvacibo.domain.ExpiryType
import com.davide.salvacibo.domain.ProductStatus
import com.davide.salvacibo.domain.Urgency
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.ProductAvatar
import com.davide.salvacibo.ui.components.Stepper
import com.davide.salvacibo.ui.theme.LocalUrgencyColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun DetailScreen(vm: AppViewModel, productId: Long, onBack: () -> Unit, onEdit: (Product) -> Unit) {
    val flow = remember(productId) { vm.observe(productId) }
    val product by flow.collectAsState(initial = null)
    val settings by vm.settings.collectAsStateWithLifecycle()

    val p = product
    if (p == null || p.status != ProductStatus.ACTIVE.name) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val info = p.expiryInfo(settings)
    var askOpenDays by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp),
        ) {
            Hero(p, info, onBack = onBack, onEdit = { onEdit(p) }, onDelete = { askDelete = true })

            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(p.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        listOfNotNull(p.brand, "${p.categoryEnum.emoji} ${p.categoryEnum.label}").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CountdownCard(p, info)
                InfoCard(p, info)
                OpenedCard(
                    product = p,
                    info = info,
                    onOpen = { askOpenDays = true },
                    onUndo = { vm.undoOpened(p) },
                )
            }
        }

        // azioni in basso
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { vm.markThrown(p); onBack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buttato")
                }
                Button(
                    onClick = { vm.markConsumed(p); onBack() },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Consumato", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (askOpenDays) {
        OpenDialog(
            product = p,
            onDismiss = { askOpenDays = false },
            onConfirm = { days ->
                vm.markOpened(p, days)
                askOpenDays = false
            },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Eliminare ${p.name}?") },
            text = { Text("Il prodotto verrà rimosso senza contarlo né come consumato né come buttato.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    vm.delete(p)
                    onBack()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Hero(p: Product, info: ExpiryInfo, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalUrgencyColors.current
    val bg = colors.soft(info.urgency)
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(bg, MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
            Spacer(Modifier.weight(1f))
            HeroButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Modifica") }
            HeroButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Elimina") }
        }
        ProductAvatar(
            category = p.categoryEnum,
            urgency = info.urgency,
            size = 168.dp,
            corner = 44.dp,
            preferPhoto = true,
            photoPath = p.photoPath,
            imageUrl = p.imageUrl,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 56.dp),
        )
    }
}

@Composable
private fun HeroButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
        ),
    ) { content() }
}

@Composable
private fun CountdownCard(p: Product, info: ExpiryInfo) {
    val colors = LocalUrgencyColors.current
    val strong = colors.strong(info.urgency)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.soft(info.urgency),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                val big = when {
                    info.daysLeft < 0 -> "${-info.daysLeft}"
                    info.daysLeft == 0L -> "Oggi"
                    else -> "${info.daysLeft}"
                }
                Text(big, color = strong, fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 52.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        info.daysLeft < 0 -> if (info.daysLeft == -1L) "giorno fa\nè scaduto" else "giorni fa\nè scaduto"
                        info.daysLeft == 0L -> "\nè l'ultimo giorno"
                        info.daysLeft == 1L -> "giorno\nrimasto"
                        else -> "giorni\nrimasti"
                    },
                    color = strong,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val until = info.openedUntil
            val opened = p.openedDate
            if (info.limitedByOpening && opened != null && until != null) {
                val total = ChronoUnit.DAYS.between(opened, until).coerceAtLeast(1)
                val elapsed = ChronoUnit.DAYS.between(opened, LocalDate.now()).coerceIn(0, total)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = strong,
                    trackColor = Color.White.copy(alpha = 0.5f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aperto da $elapsed ${if (elapsed == 1L) "giorno" else "giorni"} su $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = strong,
                )
            } else if (p.type == ExpiryType.BEST_BEFORE && info.urgency == Urgency.EXPIRED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Era \"preferibilmente entro\": guarda, annusa e assaggia. Spesso è ancora buono!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = strong,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(p: Product, info: ExpiryInfo) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            InfoRow(p.type.label, ExpiryCalc.formatShort(p.expiryDate))
            p.openedDate?.let { InfoRow("Aperto il", ExpiryCalc.formatShort(it)) }
            info.openedUntil?.let { InfoRow("Una volta aperto, entro il", ExpiryCalc.formatShort(it)) }
            if (p.openedDate == null) {
                InfoRow(
                    "Dopo l'apertura",
                    p.daysAfterOpening?.let { if (it == 1) "1 giorno" else "$it giorni" } ?: "non si applica",
                )
            }
            p.barcode?.let { InfoRow("Codice a barre", it) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun OpenedCard(product: Product, info: ExpiryInfo, onOpen: () -> Unit, onUndo: () -> Unit) {
    if (product.openedDate == null) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "L'ho aperto",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Parte il conto alla rovescia, con le notifiche",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (info.limitedByOpening) "Il conto alla rovescia dell'apertura è attivo"
                else "Aperto, ma la data stampata arriva prima",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo) { Text("Annulla apertura") }
        }
    }
}

@Composable
private fun OpenDialog(product: Product, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val suggested = product.daysAfterOpening ?: product.categoryEnum.defaultDaysAfterOpening ?: 3
    var days by remember { mutableIntStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(product.categoryEnum.emoji, fontSize = 32.sp) },
        title = { Text("Quanto dura una volta aperto?", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Stepper(value = days, onValueChange = { days = it }, range = 1..730)
                Spacer(Modifier.height(10.dp))
                val untilOpened = LocalDate.now().plusDays(days.toLong())
                val until = if (untilOpened.isBefore(product.expiryDate)) untilOpened else product.expiryDate
                Text(
                    "Ti avviso prima che passino. Da consumare entro il ${ExpiryCalc.formatShort(until)}.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(days) }, colors = ButtonDefaults.buttonColors()) { Text("Avvia") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
