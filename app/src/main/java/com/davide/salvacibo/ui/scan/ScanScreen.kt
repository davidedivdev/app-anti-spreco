package com.davide.salvacibo.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.davide.salvacibo.domain.ExpiryCalc
import com.davide.salvacibo.domain.LabelParser
import com.davide.salvacibo.domain.Urgency
import com.davide.salvacibo.ui.AppViewModel
import com.davide.salvacibo.ui.components.ProductAvatar
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private enum class Phase { BARCODE, DATE, FINISHING }

/**
 * Scansione in due passi:
 *  1. codice a barre → nome, marca, foto e categoria (memoria locale o Open Food Facts)
 *  2. data di scadenza letta dall'etichetta (OCR), più gli eventuali "giorni dopo l'apertura"
 * Poi si apre la schermata di conferma con tutto già compilato.
 */
@Composable
fun ScanScreen(vm: AppViewModel, onClose: () -> Unit, onDone: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            ScannerContent(vm, onClose, onDone, onManual)
        } else {
            PermissionNeeded(
                askedOnce = askedOnce,
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                onManual = onManual,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun ScannerContent(vm: AppViewModel, onClose: () -> Unit, onDone: () -> Unit, onManual: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var phase by remember { mutableStateOf(Phase.BARCODE) }
    val mode = remember { AtomicReference(ScanMode.BARCODE) }
    var torch by remember { mutableStateOf(false) }

    // stabilità della lettura: la stessa data deve comparire in 2 fotogrammi
    var lastDate by remember { mutableStateOf<LocalDate?>(null) }
    var hits by remember { mutableIntStateOf(0) }
    var openDaysSeen by remember { mutableStateOf<Int?>(null) }
    var showHint by remember { mutableStateOf(false) }

    fun goToDate() {
        phase = Phase.DATE
        mode.set(ScanMode.DATE)
    }

    fun finish() {
        mode.set(ScanMode.IDLE)
        phase = Phase.FINISHING
    }

    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                )
                .build()
        )
    }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) {
        onDispose {
            mode.set(ScanMode.IDLE)
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    val analyzer = remember {
        ScanAnalyzer(
            mode = mode,
            barcodeScanner = barcodeScanner,
            textRecognizer = textRecognizer,
            onBarcode = { code, frame ->
                if (phase == Phase.BARCODE) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.onBarcode(code, frame)
                    goToDate()
                }
            },
            onText = { text ->
                if (phase == Phase.DATE) {
                    val result = LabelParser.parse(text)
                    if (result.daysAfterOpening != null) openDaysSeen = result.daysAfterOpening
                    val date = result.expiry
                    if (date != null && result.score >= 1) {
                        hits = if (date == lastDate) hits + 1 else 1
                        lastDate = date
                        if (hits >= 2) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.onLabelRead(date, result.type, openDaysSeen)
                            finish()
                        }
                    }
                }
            },
        )
    }

    // suggerimento se la data non si trova
    LaunchedEffect(phase) {
        showHint = false
        if (phase == Phase.DATE) {
            delay(9_000)
            showHint = true
        }
    }

    // finito: aspetta che la ricerca del prodotto termini, poi vai alla conferma
    LaunchedEffect(phase, vm.lookingUp) {
        if (phase == Phase.FINISHING && !vm.lookingUp) {
            delay(250)
            onDone()
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(analyzer = analyzer, torchOn = torch, modifier = Modifier.fillMaxSize())
        ScanOverlay(phase = phase, modifier = Modifier.fillMaxSize())

        // barra in alto
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIcon(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
            RoundIcon(onClick = { torch = !torch }) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "Torcia")
            }
        }

        // pannello in basso
        BottomPanel(
            phase = phase,
            vm = vm,
            lastDate = lastDate,
            showHint = showHint,
            onSkip = {
                when (phase) {
                    Phase.BARCODE -> goToDate()
                    Phase.DATE -> {
                        vm.onLabelRead(null, null, openDaysSeen)
                        finish()
                    }
                    Phase.FINISHING -> Unit
                }
            },
            onManual = onManual,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
        ),
    ) { content() }
}

@Composable
private fun CameraPreview(analyzer: ImageAnalysis.Analyzer, torchOn: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)
            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torchOn) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Velo scuro con una "finestra" al centro e una linea che scorre. */
@Composable
private fun ScanOverlay(phase: Phase, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val line by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "line",
    )
    val heightFraction by animateFloatAsState(
        targetValue = if (phase == Phase.BARCODE) 0.20f else 0.16f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "frameHeight",
    )
    val accent = if (phase == Phase.DATE) Color(0xFFFFB547) else Color(0xFF7FDDB0)

    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.82f
        val h = size.height * heightFraction
        val left = (size.width - w) / 2f
        val top = size.height * 0.34f - h / 2f
        val radius = CornerRadius(28.dp.toPx())

        drawRect(Color.Black.copy(alpha = 0.5f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = GeoSize(w, h),
            cornerRadius = radius,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(left, top),
            size = GeoSize(w, h),
            cornerRadius = radius,
            style = Stroke(width = 3.dp.toPx()),
        )
        if (phase != Phase.FINISHING) {
            val y = top + h * line
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent, Color.Transparent),
                    startX = left,
                    endX = left + w,
                ),
                start = Offset(left + 16.dp.toPx(), y),
                end = Offset(left + w - 16.dp.toPx(), y),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}

@Composable
private fun BottomPanel(
    phase: Phase,
    vm: AppViewModel,
    lastDate: LocalDate?,
    showHint: Boolean,
    onSkip: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = vm.draft
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StepDot(number = 1, label = "Codice a barre", done = phase != Phase.BARCODE, active = phase == Phase.BARCODE)
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                StepDot(number = 2, label = "Scadenza", done = phase == Phase.FINISHING, active = phase == Phase.DATE)
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(targetState = phase, label = "panel") { p ->
                Column {
                    when (p) {
                        Phase.BARCODE -> {
                            Text("Inquadra il codice a barre", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Lo riconosco da solo: nome, marca e foto del prodotto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Phase.DATE -> {
                            Text("Ora inquadra la data di scadenza", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (showHint) "Non la trovo? Avvicinati, usa la torcia oppure tocca \"Salta\" e la scegli tu."
                                else "Di solito è sul tappo, sul fondo o vicino al lotto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Phase.FINISHING -> {
                            Text("Fatto!", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (vm.lookingUp) "Sto completando le informazioni del prodotto…" else "Apro la conferma…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // prodotto trovato / data letta
            if (draft.barcode != null || lastDate != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductAvatar(
                        category = draft.category,
                        urgency = Urgency.OK,
                        size = 44.dp,
                        preferPhoto = true,
                        imageUrl = draft.imageUrl,
                        photoPath = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                draft.name.isNotBlank() -> draft.name
                                vm.lookingUp -> "Cerco il prodotto…"
                                draft.barcode != null -> "Prodotto nuovo: il nome lo scrivi tu"
                                else -> "Senza codice a barre"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val date = draft.expiry ?: lastDate
                        Text(
                            if (date != null) "Scadenza: ${ExpiryCalc.formatShort(date)}" else "Scadenza: in lettura…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (vm.lookingUp) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) {
                    Text("Scrivo io")
                }
                if (phase != Phase.FINISHING) {
                    FilledTonalButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (phase == Phase.BARCODE) Icons.Filled.CalendarMonth else Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (phase == Phase.BARCODE) "Salta" else "Salta data")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDot(number: Int, label: String, done: Boolean, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done || active) scheme.primary else scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    number.toString(),
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active || done) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionNeeded(
    askedOnce: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Serve la fotocamera",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "La uso solo per leggere il codice a barre e la data di scadenza. Le immagini restano sul telefono.",
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (askedOnce) onOpenSettings else onRetry) {
            Text(if (askedOnce) "Apri le impostazioni" else "Consenti")
        }
        TextButton(onClick = onManual) { Text("Inserisci a mano", color = Color.White) }
        TextButton(onClick = onClose) { Text("Indietro", color = Color.White.copy(alpha = 0.7f)) }
    }
}
