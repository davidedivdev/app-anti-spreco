package com.davide.salvacibo.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.davide.salvacibo.domain.Category
import com.davide.salvacibo.domain.CategoryGuesser
import com.davide.salvacibo.domain.ExpiryCalc
import com.davide.salvacibo.domain.ExpiryType
import com.davide.salvacibo.domain.Urgency
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.ProductAvatar
import com.davide.salvacibo.ui.components.Stepper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Conferma (dopo la scansione) o modifica di un prodotto. Con la scansione basta toccare "Salva". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val draft = vm.draft
    val isNew = draft.id == 0L
    var categoryTouched by remember { mutableStateOf(!isNew || draft.category != Category.ALTRO) }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Conferma prodotto" else "Modifica prodotto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { vm.save(onSaved) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salva", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---------- foto + nome
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductAvatar(
                    category = draft.category,
                    urgency = Urgency.OK,
                    size = 84.dp,
                    preferPhoto = true,
                    photoPath = draft.photoPath,
                    imageUrl = draft.imageUrl,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (draft.barcode != null) "Riconosciuto dalla scansione" else "Inserimento manuale",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        draft.name.ifBlank { "Nuovo prodotto" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                    )
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { name ->
                    vm.updateDraft { d ->
                        var next = d.copy(name = name)
                        if (!categoryTouched) {
                            val g = CategoryGuesser.guess(name)
                            if (g.category != Category.ALTRO) {
                                next = next.copy(
                                    category = g.category,
                                    daysAfterOpening = if (d.daysFromLabel) d.daysAfterOpening else g.daysAfterOpening,
                                )
                            }
                        }
                        next
                    }
                },
                label = { Text("Nome") },
                placeholder = { Text("es. Petto di pollo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = draft.brand.orEmpty(),
                onValueChange = { b -> vm.updateDraft { it.copy(brand = b) } },
                label = { Text("Marca (facoltativa)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            // ---------- categoria
            Section("Categoria") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(Category.entries) { c ->
                        FilterChip(
                            selected = draft.category == c,
                            onClick = {
                                categoryTouched = true
                                vm.updateDraft { d ->
                                    d.copy(
                                        category = c,
                                        daysAfterOpening = if (d.daysFromLabel) d.daysAfterOpening else c.defaultDaysAfterOpening,
                                    )
                                }
                            },
                            label = { Text("${c.emoji}  ${c.label}") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // ---------- scadenza
            Section("Scadenza") {
                val expiry = draft.expiry
                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            if (expiry != null) {
                                Text(ExpiryCalc.formatLong(expiry), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    ExpiryCalc.relative(ChronoUnit.DAYS.between(today, expiry)).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("Scegli la data", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Non l'ho letta dall'etichetta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text("Cambia", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = listOf("Domani" to 1L, "+3 giorni" to 3L, "+1 settimana" to 7L, "+1 mese" to 30L, "+1 anno" to 365L)
                    items(quick) { (label, days) ->
                        SuggestionChip(
                            onClick = { vm.updateDraft { it.copy(expiry = today.plusDays(days)) } },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpiryType.entries.forEach { t ->
                        FilterChip(
                            selected = draft.expiryType == t,
                            onClick = { vm.updateDraft { it.copy(expiryType = t) } },
                            label = {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(t.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (t == ExpiryType.USE_BY) "data rigida (carne, pesce, latte…)" else "TMC: spesso ancora buono dopo",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }

            // ---------- durata dopo l'apertura
            Section("Una volta aperto") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Va consumato entro", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        draft.daysAfterOpening == null -> "Non si applica"
                                        draft.daysFromLabel -> "Valore letto/memorizzato"
                                        else -> "Suggerito per ${draft.category.label.lowercase()}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = draft.daysAfterOpening != null,
                                onCheckedChange = { on ->
                                    vm.updateDraft {
                                        it.copy(
                                            daysAfterOpening = if (on) (it.category.defaultDaysAfterOpening ?: 3) else null,
                                            daysFromLabel = true,
                                        )
                                    }
                                },
                            )
                        }
                        val days = draft.daysAfterOpening
                        if (days != null) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Stepper(
                                    value = days,
                                    onValueChange = { v -> vm.updateDraft { it.copy(daysAfterOpening = v, daysFromLabel = true) } },
                                    range = 1..730,
                                )
                            }
                        }
                        val opened = draft.opened
                        if (opened != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Aperto il ${ExpiryCalc.formatShort(opened)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.updateDraft { it.copy(opened = null) } }) {
                                    Text("Segna come chiuso")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val initial = (draft.expiry ?: today.plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.updateDraft { it.copy(expiry = date) }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
