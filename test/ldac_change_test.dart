import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:melo_control/core/qcy/ldac_change.dart';

void main() {
  late StreamController<int> responses;
  setUp(() => responses = StreamController<int>.broadcast(sync: true));
  tearDown(() => responses.close());

  Future<void> change({
    bool enabled = true,
    required Future<void> Function() write,
    required Future<void> Function() query,
  }) => changeLdacAndConfirm(
    enabled: enabled,
    responses: responses.stream,
    writeSetting: write,
    requestSetting: query,
    timeout: const Duration(milliseconds: 150),
    pollInterval: const Duration(milliseconds: 5),
  );

  test('captures acknowledgement arriving before GATT write returns', () async {
    await change(write: () async => responses.add(1), query: () async {});
  });

  test('queries state when the firmware does not reboot', () async {
    var writes = 0;
    var queries = 0;
    await change(
      write: () async {
        writes++;
      },
      query: () async {
        queries++;
        responses.add(1);
      },
    );
    expect(writes, 1);
    expect(queries, 1);
  });

  test('keeps querying across a temporary restart', () async {
    var queries = 0;
    await change(
      write: () async {},
      query: () async {
        queries++;
        if (queries < 3) throw StateError('BLE reconnecting');
        responses.add(1);
      },
    );
    expect(queries, 3);
  });

  test('a successful write without any earbud reply is not success', () async {
    await expectLater(
      change(write: () async {}, query: () async {}),
      throwsA(isA<StateError>()),
    );
    expect(responses.hasListener, isFalse);
  });

  test('off replies do not confirm an enable request', () async {
    await expectLater(
      change(write: () async {}, query: () async => responses.add(2)),
      throwsA(isA<StateError>()),
    );
  });

  test('invalid reply bytes never confirm either state', () async {
    await expectLater(
      change(
        enabled: false,
        write: () async {},
        query: () async => responses.add(0xff),
      ),
      throwsA(isA<StateError>()),
    );
  });

  test('confirms an explicit disable request', () async {
    await change(
      enabled: false,
      write: () async {},
      query: () async => responses.add(2),
    );
  });

  test('write failures are reported and the listener is released', () async {
    await expectLater(
      change(
        write: () async => throw StateError('GATT write failed'),
        query: () async => responses.add(1),
      ),
      throwsA(isA<StateError>()),
    );
    expect(responses.hasListener, isFalse);
  });
}
