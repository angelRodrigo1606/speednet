package com.angel.speednet.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PacketLossMathTest {

    @Test
    fun tcpRetransmitIsCountedAsLoss() {
        // Antes esto daba 0%: el connect() "exitoso" a ~1 s es un SYN retransmitido.
        val raw = listOf(20.0, 22.0, 1025.0, 21.0)
        val classified = PacketLossMath.classifyTcpSamples(raw)

        assertEquals(listOf(20.0, 22.0, null, 21.0), classified)
        val summary = PacketLossMath.summarize(classified)
        assertEquals(1, summary.losses)
        assertEquals(25.0, summary.packetLossPct, 0.01)
        assertEquals(21.0, summary.latencyMs, 0.01)
    }

    @Test
    fun tightHighLatencyIsNotLoss() {
        val samples = listOf(1100.0, 1150.0, 1080.0)
        assertEquals(samples, PacketLossMath.classifyTcpSamples(samples))
        assertEquals(0.0, PacketLossMath.summarize(samples).packetLossPct, 0.01)
    }

    @Test
    fun timeoutsAreLoss() {
        val summary = PacketLossMath.summarize(listOf(30.0, null, 28.0, null))
        assertEquals(50.0, summary.packetLossPct, 0.01)
        assertEquals(2, summary.losses)
        assertEquals(29.0, summary.latencyMs, 0.01)
    }

    @Test
    fun emptySamplesAreTotalLoss() {
        val summary = PacketLossMath.summarize(emptyList())
        assertEquals(100.0, summary.packetLossPct, 0.01)
    }

    @Test
    fun combineKeepsTheWorstSignal() {
        // Ping en reposo 0%, pero la subida retransmitió el 2.4%.
        val combined = PacketLossMath.combine(
            probeLosses = 0,
            probeAttempts = 24,
            extraLosses = 0,
            extraAttempts = 10,
            retransmissionPct = 2.36
        )
        assertEquals(2.4, combined, 0.01)
    }

    @Test
    fun combineUsesProbeLossWhenHigher() {
        val combined = PacketLossMath.combine(
            probeLosses = 3,
            probeAttempts = 12,
            extraLosses = 0,
            extraAttempts = 0,
            retransmissionPct = 0.2
        )
        assertEquals(25.0, combined, 0.01)
    }
}
