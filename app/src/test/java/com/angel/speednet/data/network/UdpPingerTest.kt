package com.angel.speednet.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class UdpPingerTest {

    @Test
    fun dnsQueryIsAStandardARecord() {
        val query = UdpPinger.dnsQuery(0x1234)
        assertEquals(0x12.toByte(), query[0])
        assertEquals(0x34.toByte(), query[1])
        assertEquals(0x01.toByte(), query[2]) // recursion desired
        assertEquals(0x01.toByte(), query[5]) // one question
        assertEquals(0.toByte(), query[4])
        // example.com + type A + class IN
        assertEquals(7, query[12].toInt())
        assertEquals('e'.code.toByte(), query[13])
        val question = query.size - 4
        assertEquals(0x01.toByte(), query[question + 1])
        assertEquals(0x01.toByte(), query[question + 3])
    }
}
