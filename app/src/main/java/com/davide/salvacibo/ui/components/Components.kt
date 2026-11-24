package com.davide.salvacibo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.davide.salvacibo.domain.Category
import com.davide.salvacibo.domain.Urgency
import com.davide.salvacibo.ui.theme.LocalUrgencyColors
import java.io.File

/**
 * Icona del prodotto. Nella lista: emoji stilizzata su sfondo colorato in base all'urgenza.
 * Con [preferPhoto] = true (pagina di dettaglio) mostra la foto vera se c'è.
 */
@Composable
fun ProductAvatar(
    category: Category,
    urgency: Urgency,
    size: Dp,
    modifier: Modifier = Modifier,
    preferPhoto: Boolean = false,
    photoPath: String? = null,
    imageUrl: String? = null,
    corner: Dp = size * 0.3f,
) {
    val colors = LocalUrgencyColors.current
    val bg by animateColorAsState(colors.soft(urgency), label = "avatarBg")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = when {
            !preferPhoto -> null
            photoPath != null && File(photoPath).exists() -> File(photoPath)
            imageUrl != null -> imageUrl
            else -> null
        }
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Emoji(category, size) },
                error = { Emoji(category, size) },
            )
        } else {
            Emoji(category, size)
        }
    }
}

@Composable
private fun Emoji(category: Category, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = category.emoji,
            fontSize = (size.value * 0.48f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pillola con il numero di giorni rimasti, colorata a semaforo. */
@Composable
fun DaysPill(daysLeft: Long, urgency: Urgency, modifier: Modifier = Modifier) {
    val colors = LocalUrgencyColors.current
    val strong by animateColorAsState(colors.strong(urgency), label = "pillStrong")
    val bg by animateColorAsState(colors.soft(urgency), label = "pillBg")
    Column(
        modifier = modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            daysLeft < 0 -> {
                Text("!", color = strong, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("scaduto", color = strong, style = MaterialTheme.typography.labelSmall)
            }
            daysLeft == 0L -> {
                Text("OGGI", color = strong, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 3.dp))
                Text("scade", color = strong, style = MaterialTheme.typography.labelSmall)
            }
            else -> {
                Text(
                    if (daysLeft > 999) "999+" else daysLeft.toString(),
                    color = strong, fontWeight = FontWeight.Black, fontSize = 22.sp,
                )
                Text(if (daysLeft == 1L) "giorno" else "giorni", color = strong, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Piccola etichetta colorata (es. "Aperto", "TMC"). */
@Composable
fun Tag(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Selettore numerico  [-]  3 giorni  [+] */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..365,
    suffix: (Int) -> String = { if (it == 1) "giorno" else "giorni" },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
        ) { Icon(Icons.Filled.Remove, contentDescription = "Meno") }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(suffix(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
        ) { Icon(Icons.Filled.Add, contentDescription = "Più") }
    }
}
