import 'package:flutter_test/flutter_test.dart';
import 'package:melo_control/core/qcy/constants.dart';
import 'package:melo_control/core/qcy/product_features.dart';

void main() {
  test('HT05 exposes a direct experiment without claiming LDAC support', () {
    for (final vendor in [19777, 19778]) {
      final features = featuresForVendor(vendor);
      expect(features.ldac, isFalse);
      expect(features.ldacExperimental, isTrue);
      expect(features.canRequestLdac, isTrue);
    }
  });

  test('MeloBuds Pro retains its advertised LDAC control', () {
    final features = featuresForVendor(meloBudsProVendorId);
    expect(features.ldac, isTrue);
    expect(features.ldacExperimental, isFalse);
    expect(features.canRequestLdac, isTrue);
  });

  test('unknown models do not inherit an LDAC support claim', () {
    expect(featuresForVendor(-1).canRequestLdac, isFalse);
  });
}
