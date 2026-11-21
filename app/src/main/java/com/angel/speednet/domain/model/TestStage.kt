package com.angel.speednet.domain.model

/**
 * Etapas de una medición. La UI usa este enum para mostrar el progreso.
 */
enum class TestStage {
    IDLE,
    CHECKING_NETWORK,
    READING_SIGNAL,
    MEASURING_LATENCY,
    DOWNLOADING,
    UPLOADING,
    COMPUTING_SCORE,
    DONE,
    FAILED
}
