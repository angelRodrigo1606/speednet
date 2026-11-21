package com.angel.speednet.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.scoring.ScoreCalculator
import com.angel.speednet.ui.components.LevelBadge
import com.angel.speednet.ui.components.MetricCard
import com.angel.speednet.ui.components.ScoreRing
import com.angel.speednet.ui.components.VerdictCard
import com.angel.speednet.ui.levelColor
import com.angel.speednet.ui.networkTypeLabel
import com.angel.speednet.ui.pointsColor
import com.angel.speednet.ui.pointsLabel
import com.angel.speednet.ui.useEmoji
import com.angel.speednet.ui.useLabel
import com.angel.speednet.ui.verdictColor
import com.angel.speednet.ui.verdictLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    measurementId: Long,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = viewModel(factory = ResultsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(measurementId) {
        viewModel.load(measurementId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val current = state) {
            ResultsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is ResultsUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is ResultsUiState.Success -> ResultsContent(
                measurement = current.measurement,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ResultsContent(measurement: Measurement, modifier: Modifier = Modifier) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScoreRing(
                    progress = measurement.score / 100f,
                    ringColor = levelColor(measurement.level),
                    diameter = 220.dp
                ) {
                    Text(
                        text = measurement.score.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LevelBadge(level = measurement.level)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = dateFormatter.format(Date(measurement.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Descarga",
                    value = formatSpeed(measurement.downloadMbps),
                    unit = "Mbps",
                    quality = pointsLabel(ScoreCalculator.downloadScore(measurement.downloadMbps)),
                    qualityColor = pointsColor(ScoreCalculator.downloadScore(measurement.downloadMbps)),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Subida",
                    value = formatSpeed(measurement.uploadMbps),
                    unit = "Mbps",
                    quality = pointsLabel(ScoreCalculator.uploadScore(measurement.uploadMbps)),
                    qualityColor = pointsColor(ScoreCalculator.uploadScore(measurement.uploadMbps)),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Latencia",
                    value = measurement.latencyMs.roundToInt().toString(),
                    unit = "ms",
                    quality = pointsLabel(ScoreCalculator.latencyScore(measurement.latencyMs)),
                    qualityColor = pointsColor(ScoreCalculator.latencyScore(measurement.latencyMs)),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Jitter",
                    value = formatOneDecimal(measurement.jitterMs),
                    unit = "ms",
                    quality = pointsLabel(ScoreCalculator.jitterScore(measurement.jitterMs)),
                    qualityColor = pointsColor(ScoreCalculator.jitterScore(measurement.jitterMs)),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            MetricCard(
                title = "Pérdida de paquetes",
                value = formatOneDecimal(measurement.packetLossPct),
                unit = "%",
                quality = pointsLabel(ScoreCalculator.lossScore(measurement.packetLossPct)),
                qualityColor = pointsColor(ScoreCalculator.lossScore(measurement.packetLossPct)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SignalCard(measurement)
        }

        item {
            Text(
                text = "Veredictos por uso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(NetworkUse.entries.toList()) { use ->
            val verdict = measurement.verdicts[use]
            if (verdict != null) {
                VerdictCard(
                    emoji = useEmoji(use),
                    title = useLabel(use),
                    verdictText = verdictLabel(verdict),
                    verdictColor = verdictColor(verdict)
                )
            }
        }

        item {
            LocationText(measurement)
        }
    }
}

@Composable
private fun SignalCard(measurement: Measurement) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Red",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = networkTypeLabel(measurement.network.type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            measurement.network.ssid?.let {
                Text(text = "SSID: $it", style = MaterialTheme.typography.bodySmall)
            }
            measurement.network.carrierName?.let {
                Text(text = "Operador: $it", style = MaterialTheme.typography.bodySmall)
            }
            measurement.network.signalDbm?.let {
                Text(text = "Señal: $it dBm", style = MaterialTheme.typography.bodySmall)
            }
            if (measurement.network.ssid == null && measurement.network.signalDbm == null) {
                Text(
                    text = "Sin detalle de señal (permiso de ubicación no concedido).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocationText(measurement: Measurement) {
    val latitude = measurement.latitude
    val longitude = measurement.longitude
    if (latitude != null && longitude != null) {
        Text(
            text = "Ubicación: %.4f, %.4f".format(Locale.US, latitude, longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun formatSpeed(value: Double): String =
    if (value >= 100) "%.0f".format(value) else "%.1f".format(value)

private fun formatOneDecimal(value: Double): String = "%.1f".format(value)
