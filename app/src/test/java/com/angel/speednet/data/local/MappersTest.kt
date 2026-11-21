package com.angel.speednet.data.local

import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.model.NetworkSignal
import com.angel.speednet.domain.model.NetworkType
import com.angel.speednet.domain.model.NetworkUse
import com.angel.speednet.domain.model.ScoreLevel
import com.angel.speednet.domain.model.UseVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun entityRoundTripPreservesMeasurement() {
        val measurement = Measurement(
            id = 7L,
            timestamp = 1_700_000_000_000L,
            downloadMbps = 87.5,
            uploadMbps = 21.3,
            latencyMs = 24.0,
            jitterMs = 4.2,
            packetLossPct = 0.0,
            score = 84,
            level = ScoreLevel.EXCELLENT,
            network = NetworkSignal(
                type = NetworkType.WIFI,
                signalDbm = -52,
                ssid = "MiRed",
                carrierName = null
            ),
            latitude = -34.6037,
            longitude = -58.3816,
            verdicts = mapOf(
                NetworkUse.VIDEO_CALL to UseVerdict.OPTIMAL,
                NetworkUse.STREAMING to UseVerdict.OPTIMAL,
                NetworkUse.GAMING to UseVerdict.ACCEPTABLE,
                NetworkUse.BROWSING to UseVerdict.OPTIMAL
            )
        )

        val restored = measurement.toEntity().toDomain()

        assertEquals(measurement, restored)
    }
}
