package com.angel.speednet.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TcpRetransmissionSamplerTest {

    private val snmp = """
        Ip: Forwarding DefaultTTL InReceives
        Ip: 2 64 10
        Tcp: RtoAlgorithm RtoMin RtoMax MaxConn ActiveOpens PassiveOpens AttemptFails EstabResets CurrEstab InSegs OutSegs RetransSegs InErrs OutRsts
        Tcp: 1 200 120000 -1 5 0 0 0 1 1000 800 4 0 0
        Udp: InDatagrams
        Udp: 3
    """.trimIndent()

    @Test
    fun parsesRetransmissionCounters() {
        val snapshot = TcpRetransmissionSampler.parse(snmp)
        assertEquals(800L, snapshot?.outSegs)
        assertEquals(4L, snapshot?.retransSegs)
    }

    @Test
    fun lossPctUsesTheDelta() {
        val before = TcpRetransmissionSampler.Snapshot(outSegs = 800, retransSegs = 4)
        val after = TcpRetransmissionSampler.Snapshot(outSegs = 1800, retransSegs = 24)
        assertEquals(2.0, TcpRetransmissionSampler.lossPct(before, after)!!, 0.01)
    }

    @Test
    fun ignoresTinyOrInconsistentDeltas() {
        val before = TcpRetransmissionSampler.Snapshot(outSegs = 10, retransSegs = 0)
        val after = TcpRetransmissionSampler.Snapshot(outSegs = 20, retransSegs = 1)
        assertNull(TcpRetransmissionSampler.lossPct(before, after))

        val wrapped = TcpRetransmissionSampler.Snapshot(outSegs = 5, retransSegs = 0)
        assertNull(TcpRetransmissionSampler.lossPct(after, wrapped))
    }
}
