package com.angel.speednet.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.angel.speednet.domain.model.TestStage
import com.angel.speednet.domain.scoring.ScoreCalculator
import com.angel.speednet.ui.components.ScoreRing
import com.angel.speednet.ui.levelColor
import com.angel.speednet.ui.stageLabel

@Composable
fun HomeScreen(
    onResult: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDataWarning by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Medimos aunque se deniegue: la prueba sigue en modo degradado.
        viewModel.startTest()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect { id ->
            if (id > 0) onResult(id)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Speednet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Calidad de tu conexión",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            when (val current = state) {
                is HomeUiState.Idle -> IdleContent(
                    lastScore = current.lastScore,
                    onMeasure = { showDataWarning = true },
                    onOpenHistory = onOpenHistory
                )

                is HomeUiState.Measuring -> MeasuringContent(
                    state = current,
                    onCancel = viewModel::cancelTest
                )

                is HomeUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = { showDataWarning = true },
                    onDismiss = viewModel::dismissError
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (showDataWarning) {
        AlertDialog(
            onDismissRequest = { showDataWarning = false },
            title = { Text("Consumo de datos") },
            text = {
                Text("Esta prueba puede consumir hasta ~60 MB de tu plan de datos. ¿Deseas continuar?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDataWarning = false
                        val missing = REQUIRED_PERMISSIONS.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            viewModel.startTest()
                        }
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDataWarning = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun IdleContent(
    lastScore: Int?,
    onMeasure: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val level = lastScore?.let { ScoreCalculator.levelFor(it) }
    ScoreRing(
        progress = (lastScore ?: 0) / 100f,
        ringColor = level?.let { levelColor(it) } ?: MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = lastScore?.toString() ?: "—",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (lastScore != null) "Último puntaje" else "Sin mediciones",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onMeasure,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text("Medir ahora", style = MaterialTheme.typography.titleMedium)
    }

    Spacer(Modifier.height(8.dp))

    TextButton(onClick = onOpenHistory) {
        Text("Ver historial")
    }
}

@Composable
private fun MeasuringContent(
    state: HomeUiState.Measuring,
    onCancel: () -> Unit
) {
    val overall = overallProgress(state.stage, state.progress)

    ScoreRing(
        progress = overall,
        ringColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "${(overall * 100).toInt()}%",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stageLabel(state.stage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    LinearProgressIndicator(
        progress = { overall },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Cancelar")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = "No se pudo medir",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Reintentar")
    }
    TextButton(onClick = onDismiss) {
        Text("Volver")
    }
}

private fun overallProgress(stage: TestStage, stageProgress: Float): Float {
    val (base, span) = when (stage) {
        TestStage.CHECKING_NETWORK -> 0f to 0.05f
        TestStage.READING_SIGNAL -> 0.05f to 0.05f
        TestStage.MEASURING_LATENCY -> 0.10f to 0.25f
        TestStage.DOWNLOADING -> 0.35f to 0.30f
        TestStage.UPLOADING -> 0.65f to 0.25f
        TestStage.COMPUTING_SCORE -> 0.90f to 0.10f
        else -> 0f to 0f
    }
    return (base + span * stageProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_PHONE_STATE
)
