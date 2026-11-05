# SoundPEATS SPP read-only probe

Reference: https://github.com/Wasabules/OpenPeats/blob/main/PROTOCOL.md

The reference was validated on a SoundPEATS H3. It does not establish that a
QCY HT05 or a Capsule3 Pro exposes the same endpoint. This independent probe
tests that hypothesis on a single bonded device explicitly selected in the UI.

The app enumerates the selected device's SDP UUIDs (Android may return cached
records), then makes one bounded connection attempt to the public SPP UUID
`00001101-0000-1000-8000-00805f9b34fb`. It does not scan numbered channels or
use hidden APIs. A failed connection does not prove the absence of another
endpoint or the absence of LDAC decoding support.

Only three SoundPEATS GET requests are implemented:

| Request | Frame | Preconditions |
| --- | --- | --- |
| Firmware | `FF 04 00 00 00 0A 03 09` | Connected SPP socket |
| Left battery | `FF 04 00 00 00 0A 03 06` | Connected SPP socket |
| LDAC setting | `FF 04 00 00 00 0A 03 38` | A validated firmware/battery reply |

The packet builder rejects all other opcodes, including the LDAC SET opcode.
Read replies require the documented frame version, flags, vendor ID, GET reply
opcode, status and payload. RFCOMM fragmentation and coalescing are handled.
The diagnostic does not change firmware, pairing, codec settings, or volume.

Limits: 6 seconds for SDP, 8 seconds for connection, 1.5 seconds per read attempt,
and a 30-second overall socket deadline. The socket, timer and receiver are
released after the probe or when the activity is destroyed.

Device addresses appear only in the local device selector to distinguish
paired devices. They are omitted/redacted from the generated diagnostic.
Other paired devices are not contacted. Diagnostics are copied only by user
action and are not uploaded automatically.

Tests cover the exact GET bytes, rejected SETs, fragmented/coalesced replies,
stream resynchronisation, unrelated vendors/opcodes, invalid states, battery
ranges and firmware string validation. Physical HT05 compatibility remains
unverified until the user runs the probe.
