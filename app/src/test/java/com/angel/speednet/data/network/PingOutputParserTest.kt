package com.angel.speednet.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingOutputParserTest {

    @Test
    fun parsesIputilsSuccess() {
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms

            --- 1.1.1.1 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
            rtt min/avg/max/mdev = 12.3/12.3/12.3/0.0 ms
        """.trimIndent()

        val interpreted = PingOutputParser.interpret(0, output)
        val sample = interpreted as PingOutputParser.Interpreted.Sample
        assertFalse(sample.lost)
        assertEquals(12.3, sample.rttMs!!, 0.01)
    }

    @Test
    fun parsesTimeoutAsLoss() {
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.

            --- 1.1.1.1 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 1000ms
        """.trimIndent()

        val sample = PingOutputParser.interpret(1, output) as PingOutputParser.Interpreted.Sample
        assertTrue(sample.lost)
        assertNull(sample.rttMs)
    }

    @Test
    fun parsesBusyboxSummary() {
        val output = """
            PING 8.8.8.8 (8.8.8.8): 56 data bytes
            64 bytes from 8.8.8.8: seq=0 ttl=116 time=18.45 ms

            --- 8.8.8.8 ping statistics ---
            1 packets transmitted, 1 packets received, 0% packet loss
            round-trip min/avg/max = 18.45/18.45/18.45 ms
        """.trimIndent()

        val sample = PingOutputParser.interpret(0, output) as PingOutputParser.Interpreted.Sample
        assertFalse(sample.lost)
        assertEquals(18.45, sample.rttMs!!, 0.01)
    }

    @Test
    fun permissionDeniedIsUnusable() {
        val interpreted = PingOutputParser.interpret(
            2,
            "ping: icmp open socket: Operation not permitted"
        )
        assertTrue(interpreted is PingOutputParser.Interpreted.Unusable)
    }

    @Test
    fun ignoresDuplicateReplies() {
        val output = """
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=10.0 ms
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=40.0 ms (DUP!)
            1 packets transmitted, 1 received, 0% packet loss
        """.trimIndent()

        val parsed = PingOutputParser.parse(output)
        assertEquals(listOf(10.0), parsed.rttsMs)
        assertEquals(1, parsed.received)
    }

    @Test
    fun timeBelowOneMillisecond() {
        val output = """
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=64 time<1 ms
            1 packets transmitted, 1 received, 0% packet loss
        """.trimIndent()

        val sample = PingOutputParser.interpret(0, output) as PingOutputParser.Interpreted.Sample
        assertFalse(sample.lost)
        assertEquals(1.0, sample.rttMs!!, 0.01)
    }
}
