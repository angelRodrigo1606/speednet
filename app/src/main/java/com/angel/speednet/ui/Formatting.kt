package com.angel.speednet.ui

import androidx.compose.ui.graphics.Color
import com.angel.speednet.domain.model.NetworkType
import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.model.ScoreLevel
import com.angel.speednet.domain.model.TestStage
import com.angel.speednet.domain.model.UseVerdict
import com.angel.speednet.domain.scoring.ScoreCalculator
import com.angel.speednet.ui.theme.LevelExcellent
import com.angel.speednet.ui.theme.LevelFair
import com.angel.speednet.ui.theme.LevelGood
import com.angel.speednet.ui.theme.LevelPoor

fun levelLabel(level: ScoreLevel): String = when (level) {
    ScoreLevel.EXCELLENT -> "Excelente"
    ScoreLevel.GOOD -> "Buena"
    ScoreLevel.FAIR -> "Regular"
    ScoreLevel.POOR -> "Mala"
}

fun levelColor(level: ScoreLevel): Color = when (level) {
    ScoreLevel.EXCELLENT -> LevelExcellent
    ScoreLevel.GOOD -> LevelGood
    ScoreLevel.FAIR -> LevelFair
    ScoreLevel.POOR -> LevelPoor
}

fun pointsColor(points: Int): Color = levelColor(ScoreCalculator.levelFor(points))

fun pointsLabel(points: Int): String = levelLabel(ScoreCalculator.levelFor(points))

fun verdictLabel(verdict: UseVerdict): String = when (verdict) {
    UseVerdict.OPTIMAL -> "Óptima"
    UseVerdict.ACCEPTABLE -> "Aceptable"
    UseVerdict.NOT_RECOMMENDED -> "No recomendada"
}

fun verdictColor(verdict: UseVerdict): Color = when (verdict) {
    UseVerdict.OPTIMAL -> LevelExcellent
    UseVerdict.ACCEPTABLE -> LevelFair
    UseVerdict.NOT_RECOMMENDED -> LevelPoor
}

fun useLabel(use: NetworkUse): String = when (use) {
    NetworkUse.VIDEO_CALL -> "Videollamadas"
    NetworkUse.STREAMING -> "Streaming"
    NetworkUse.GAMING -> "Juegos"
    NetworkUse.BROWSING -> "Navegación"
}

fun useEmoji(use: NetworkUse): String = when (use) {
    NetworkUse.VIDEO_CALL -> "\uD83D\uDCF9"
    NetworkUse.STREAMING -> "\uD83C\uDFAC"
    NetworkUse.GAMING -> "\uD83C\uDFAE"
    NetworkUse.BROWSING -> "\uD83C\uDF10"
}

fun stageLabel(stage: TestStage): String = when (stage) {
    TestStage.IDLE -> "Listo"
    TestStage.CHECKING_NETWORK -> "Comprobando red…"
    TestStage.READING_SIGNAL -> "Leyendo señal…"
    TestStage.MEASURING_LATENCY -> "Midiendo latencia…"
    TestStage.DOWNLOADING -> "Midiendo descarga…"
    TestStage.UPLOADING -> "Midiendo subida…"
    TestStage.COMPUTING_SCORE -> "Calculando puntaje…"
    TestStage.DONE -> "Completado"
    TestStage.FAILED -> "Error"
}

fun networkTypeLabel(type: NetworkType): String = when (type) {
    NetworkType.WIFI -> "Wi-Fi"
    NetworkType.CELLULAR_4G -> "4G / LTE"
    NetworkType.CELLULAR_5G -> "5G"
    NetworkType.CELLULAR_OTHER -> "Red móvil"
    NetworkType.UNKNOWN -> "Desconocida"
}
