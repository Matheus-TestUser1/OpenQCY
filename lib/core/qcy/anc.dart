import 'packet.dart';

/// MeloBuds Pro ANC modes using QCY packed cmdids (command 0x17).
enum AncMode {
  off,
  indoor,
  commuting,
  noisy,
  antiWind,
  adaptive,
  transparency;

  String get label => switch (this) {
        AncMode.off => 'Off',
        AncMode.indoor => 'ANC',
        AncMode.commuting => 'Outdoor',
        AncMode.noisy => 'Noisy',
        AncMode.antiWind => 'Wind',
        AncMode.adaptive => 'Adaptive',
        AncMode.transparency => 'Transparency',
      };

  /// Packed cmdid at level 0 (products.json `startcmdid`).
  int get basePacked => switch (this) {
        AncMode.off => 131072,
        AncMode.indoor => 65792,
        AncMode.commuting => 66048,
        AncMode.noisy => 66304,
        AncMode.antiWind => 66560,
        AncMode.adaptive => 66816,
        AncMode.transparency => 196864,
      };

  /// Selectable levels; the level is the low (noise) byte of the cmdid.
  int get levels => switch (this) {
        AncMode.indoor || AncMode.commuting || AncMode.noisy => 3,
        AncMode.transparency => 7,
        _ => 1,
      };

  /// products.json `defaultcmd` level.
  int get defaultLevel => switch (this) {
        AncMode.indoor || AncMode.commuting || AncMode.noisy => 2,
        AncMode.transparency => 4,
        _ => 0,
      };

  int get packed => packedAt(defaultLevel);

  int packedAt(int level) => basePacked + level.clamp(0, levels - 1);

  /// Decode mode/sub/noise from a 0x17 notification ack.
  static AncMode? fromAck(int mode, int subScene, int noise) {
    for (final m in AncMode.values) {
      final (mMode, mSub, _) = unpackPacked(m.basePacked);
      if (mMode == mode && mSub == subScene && noise < m.levels) {
        return m;
      }
    }
    // Transparency ack sometimes reports sub=1.
    if (mode == 3 && (subScene == 1 || subScene == 2)) {
      return AncMode.transparency;
    }
    if (mode == 2 && subScene == 0 && noise == 0) return AncMode.off;
    if (mode == 1 && subScene == 1) return AncMode.indoor;
    if (mode == 1 && subScene == 2) return AncMode.commuting;
    if (mode == 1 && subScene == 5) return AncMode.adaptive;
    return null;
  }
}

(int mode, int subScene, int noise) unpackPacked(int packed) {
  return ((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF);
}

List<int> buildAncCommand(AncMode mode, [int? level]) {
  var (m, sub, noise) = unpackPacked(mode.packedAt(level ?? mode.defaultLevel));
  // Official app remaps transparency (3,1,0) → (3,2,0).
  if (m == 3 && sub == 1 && noise == 0) {
    sub = 2;
  }
  return packCommand(0x17, [m, sub, noise]);
}
