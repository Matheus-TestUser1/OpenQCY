package com.httpkiwi.melocontrol

/** Independent, read-only implementation of the public OpenPeats protocol notes.
 * Reference: https://github.com/Wasabules/OpenPeats/blob/main/PROTOCOL.md
 * No control writes, firmware commands, or channel scanning are exposed here.
 */
object PeatsReadProtocol {
    const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    const val VERSION = 0x09
    const val BATTERY = 0x06
    const val LDAC = 0x38

    fun request(command: Int): ByteArray {
        require(command in setOf(VERSION, BATTERY, LDAC)) { "Only GET commands are permitted" }
        return byteArrayOf(0xff.toByte(), 0x04, 0, 0, 0, 0x0a, 0x03, command.toByte())
    }

    data class Frame(val version: Int, val flags: Int, val vendor: Int, val command: Int, val payload: ByteArray) {
        fun matches(getCommand: Int): Boolean =
            version in 3..4 && flags == 0 && vendor == 0x000a && command == (0x8300 or getCommand)

        fun firmware(): String? {
            if (!matches(VERSION) || payload.size < 2 || payload[0] != 0.toByte()) return null
            val text = payload.drop(1).map { it.toInt() and 0xff }.dropLastWhile { it == 0 || it == 32 }
            if (text.isEmpty() || text.any { it !in 32..126 }) return null
            return text.map { it.toChar() }.joinToString("")
        }

        fun battery(): Int? {
            if (!matches(BATTERY) || payload.size != 2 || payload[0] != 0.toByte()) return null
            return (payload[1].toInt() and 0xff).takeIf { it <= 100 }
        }

        fun ldac(): Boolean? {
            if (!matches(LDAC) || payload.size != 2 || payload[0] != 0.toByte()) return null
            return when (payload[1].toInt() and 0xff) { 0 -> false; 1 -> true; else -> null }
        }
    }

    /** RFCOMM is a byte stream: preserve fragments and decode coalesced replies. */
    class Decoder {
        private val pending = mutableListOf<Int>()

        fun feed(data: ByteArray): List<Frame> {
            pending.addAll(data.map { it.toInt() and 0xff })
            val frames = mutableListOf<Frame>()
            while (pending.isNotEmpty()) {
                if (pending[0] != 0xff) { pending.removeAt(0); continue }
                if (pending.size < 2) break
                if (pending[1] !in 3..4) { pending.removeAt(0); continue }
                if (pending.size < 8) break
                val length = 8 + pending[3]
                if (pending.size < length) break
                frames.add(Frame(pending[1], pending[2], (pending[4] shl 8) or pending[5],
                    (pending[6] shl 8) or pending[7], pending.subList(8, length).map { it.toByte() }.toByteArray()))
                pending.subList(0, length).clear()
            }
            return frames
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
}
