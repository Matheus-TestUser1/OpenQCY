package com.httpkiwi.melocontrol

import org.junit.Assert.*
import org.junit.Test

class PeatsReadProtocolTest {
    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun frame(hex: String) = PeatsReadProtocol.Decoder().feed(bytes(hex)).single()

    @Test fun versionRequestMatchesPublicWireFormat() {
        assertArrayEquals(bytes("ff 04 00 00 00 0a 03 09"), PeatsReadProtocol.request(PeatsReadProtocol.VERSION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun activationCommandsCannotBeBuiltByTheReadOnlyProbe() {
        PeatsReadProtocol.request(0x39)
    }

    @Test fun fragmentedRepliesWaitForTheirRemainingBytes() {
        val decoder = PeatsReadProtocol.Decoder()
        assertTrue(decoder.feed(bytes("ff 03 00 02 00")).isEmpty())
        val result = decoder.feed(bytes("0a 83 38 00 01")).single()
        assertEquals(true, result.ldac())
    }

    @Test fun multipleRepliesCanArriveInOneRead() {
        val result = PeatsReadProtocol.Decoder().feed(bytes("ff 03 00 02 00 0a 83 06 00 2a ff 03 00 02 00 0a 83 38 00 00"))
        assertEquals(2, result.size)
        assertEquals(42, result[0].battery())
        assertEquals(false, result[1].ldac())
    }

    @Test fun noiseAndInvalidVersionsDoNotHideTheNextFrame() {
        val result = PeatsReadProtocol.Decoder().feed(bytes("10 11 ff 07 20 ff 03 00 02 00 0a 83 38 00 01"))
        assertEquals(true, result.single().ldac())
    }

    @Test fun anotherVendorCannotConfirmLdac() {
        assertNull(frame("ff 03 00 02 00 1d 83 38 00 01").ldac())
    }

    @Test fun unknownLdacStateIsNotReportedAsEnabledOrDisabled() {
        assertNull(frame("ff 03 00 02 00 0a 83 38 00 02").ldac())
    }

    @Test fun errorStatusCannotConfirmLdac() {
        assertNull(frame("ff 03 00 02 00 0a 83 38 01 01").ldac())
    }

    @Test fun wrongOpcodeCannotConfirmLdac() {
        assertNull(frame("ff 03 00 02 00 0a 83 39 00 01").ldac())
    }

    @Test fun impossibleBatteryValueIsNotAnIdentityReply() {
        assertNull(frame("ff 03 00 02 00 0a 83 06 00 ff").battery())
    }

    @Test fun firmwareReadStripsOnlyStatusAndTrailingPadding() {
        assertEquals("S5_1", frame("ff 03 00 07 00 0a 83 09 00 53 35 5f 31 00 20").firmware())
    }

    @Test fun binaryDataIsNotMistakenForAFirmwareString() {
        assertNull(frame("ff 03 00 03 00 0a 83 09 00 01 ff").firmware())
    }
}
