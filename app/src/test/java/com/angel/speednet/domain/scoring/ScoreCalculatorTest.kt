package com.angel.speednet.domain.scoring

import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.model.ScoreLevel
import com.angel.speednet.domain.model.UseVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun downloadScoreUsesBands() {
        assertEquals(100, ScoreCalculator.downloadScore(120.0))
        assertEquals(100, ScoreCalculator.downloadScore(100.0))
        assertEquals(90, ScoreCalculator.downloadScore(50.0))
        assertEquals(75, ScoreCalculator.downloadScore(30.0))
        assertEquals(55, ScoreCalculator.downloadScore(15.0))
        assertEquals(35, ScoreCalculator.downloadScore(7.0))
        assertEquals(15, ScoreCalculator.downloadScore(3.0))
        assertEquals(0, ScoreCalculator.downloadScore(1.0))
    }

    @Test
    fun latencyScoreIsLowerIsBetter() {
        assertEquals(100, ScoreCalculator.latencyScore(10.0))
        assertEquals(90, ScoreCalculator.latencyScore(20.0))
        assertEquals(75, ScoreCalculator.latencyScore(45.0))
        assertEquals(55, ScoreCalculator.latencyScore(60.0))
        assertEquals(35, ScoreCalculator.latencyScore(120.0))
        assertEquals(15, ScoreCalculator.latencyScore(160.0))
        assertEquals(0, ScoreCalculator.latencyScore(250.0))
    }

    @Test
    fun lossScoreHandlesPercentages() {
        assertEquals(100, ScoreCalculator.lossScore(0.0))
        assertEquals(90, ScoreCalculator.lossScore(0.4))
        assertEquals(75, ScoreCalculator.lossScore(0.8))
        assertEquals(55, ScoreCalculator.lossScore(1.5))
        assertEquals(35, ScoreCalculator.lossScore(2.5))
        assertEquals(15, ScoreCalculator.lossScore(4.0))
        assertEquals(0, ScoreCalculator.lossScore(6.0))
    }

    @Test
    fun perfectNetworkScoresOneHundred() {
        val score = ScoreCalculator.overallScore(
            downloadMbps = 200.0,
            uploadMbps = 100.0,
            latencyMs = 8.0,
            jitterMs = 1.0,
            packetLossPct = 0.0
        )
        assertEquals(100, score)
    }

    @Test
    fun levelBoundaries() {
        assertEquals(ScoreLevel.EXCELLENT, ScoreCalculator.levelFor(100))
        assertEquals(ScoreLevel.EXCELLENT, ScoreCalculator.levelFor(80))
        assertEquals(ScoreLevel.GOOD, ScoreCalculator.levelFor(79))
        assertEquals(ScoreLevel.GOOD, ScoreCalculator.levelFor(60))
        assertEquals(ScoreLevel.FAIR, ScoreCalculator.levelFor(59))
        assertEquals(ScoreLevel.FAIR, ScoreCalculator.levelFor(40))
        assertEquals(ScoreLevel.POOR, ScoreCalculator.levelFor(39))
        assertEquals(ScoreLevel.POOR, ScoreCalculator.levelFor(0))
    }

    @Test
    fun verdictsForExcellentNetworkAreOptimal() {
        val verdicts = VerdictCalculator.verdicts(
            downloadMbps = 200.0,
            uploadMbps = 100.0,
            latencyMs = 8.0,
            jitterMs = 1.0,
            packetLossPct = 0.0
        )
        NetworkUse.entries.forEach { use ->
            assertEquals(UseVerdict.OPTIMAL, verdicts[use])
        }
    }

    @Test
    fun verdictsForPoorNetworkAreNotRecommended() {
        val verdicts = VerdictCalculator.verdicts(
            downloadMbps = 1.0,
            uploadMbps = 0.5,
            latencyMs = 300.0,
            jitterMs = 100.0,
            packetLossPct = 10.0
        )
        NetworkUse.entries.forEach { use ->
            assertEquals(UseVerdict.NOT_RECOMMENDED, verdicts[use])
        }
    }
}
