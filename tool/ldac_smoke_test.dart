import 'dart:async';
import '../lib/core/qcy/ldac_change.dart';

Future<void> main() async {
  var passed = 0;
  Future<void> scenario(String name, Future<void> Function(StreamController<int>) body) async {
    final replies = StreamController<int>.broadcast(sync: true);
    try {
      await body(replies);
      if (replies.hasListener) throw StateError('Leaked reply listener');
      passed++;
      print('PASS $name');
    } finally {
      await replies.close();
    }
  }

  Future<void> change(StreamController<int> replies, {
    bool enabled = true,
    required Future<void> Function() write,
    required Future<void> Function() query,
  }) => changeLdacAndConfirm(
    enabled: enabled,
    responses: replies.stream,
    writeSetting: write,
    requestSetting: query,
    timeout: const Duration(milliseconds: 150),
    pollInterval: const Duration(milliseconds: 5),
  );

  Future<void> mustFail(Future<void> operation) async {
    var failed = false;
    try { await operation; } on StateError { failed = true; }
    if (!failed) throw StateError('Expected a rejected/unconfirmed operation');
  }

  await scenario('ack before GATT write returns', (r) async {
    await change(r, write: () async => r.add(1), query: () async {});
  });
  await scenario('readback without reboot; only one enable write', (r) async {
    var writes = 0;
    var queries = 0;
    await change(r, write: () async { writes++; }, query: () async {
      queries++;
      r.add(1);
    });
    if (writes != 1 || queries != 1) throw StateError('Unexpected write/query count');
  });
  await scenario('temporary link loss then confirmed readback', (r) async {
    var queries = 0;
    await change(r, write: () async {}, query: () async {
      if (++queries < 3) throw StateError('Reconnecting');
      r.add(1);
    });
    if (queries != 3) throw StateError('Did not retry as expected');
  });
  await scenario('write success without reply is not activation', (r) async {
    await mustFail(change(r, write: () async {}, query: () async {}));
  });
  await scenario('disabled reply cannot confirm enable', (r) async {
    await mustFail(change(r, write: () async {}, query: () async => r.add(2)));
  });
  await scenario('invalid reply cannot confirm disable', (r) async {
    await mustFail(change(r, enabled: false, write: () async {}, query: () async => r.add(255)));
  });
  await scenario('explicit disable confirmation', (r) async {
    await change(r, enabled: false, write: () async {}, query: () async => r.add(2));
  });
  await scenario('GATT write error propagates', (r) async {
    await mustFail(change(r, write: () async => throw StateError('write failed'), query: () async => r.add(1)));
  });
  print('$passed/8 simulated protocol scenarios passed; no physical Bluetooth test.');
}
