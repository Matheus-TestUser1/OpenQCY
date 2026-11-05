import 'dart:async';

/// Confirms the earbud's setting, not the Android A2DP codec in use.
/// A successful GATT write alone is deliberately not treated as confirmation.
Future<void> changeLdacAndConfirm({
  required bool enabled,
  required Stream<int> responses,
  required Future<void> Function() writeSetting,
  required Future<void> Function() requestSetting,
  void Function(String)? log,
  Duration timeout = const Duration(seconds: 35),
  Duration pollInterval = const Duration(seconds: 3),
  int maxRequests = 8,
}) async {
  final confirmed = Completer<void>();
  int? lastState;
  var closed = false;
  var queryInFlight = false;
  var requests = 0;
  final subscription = responses.listen((value) {
    if (value != 0x01 && value != 0x02) {
      log?.call('Resposta LDAC desconhecida: 0x${value.toRadixString(16)}');
      return;
    }
    lastState = value;
    if (value == (enabled ? 0x01 : 0x02) && !confirmed.isCompleted) {
      confirmed.complete();
    }
  });
  Timer? timer;

  Future<void> query() async {
    if (closed || confirmed.isCompleted || queryInFlight) return;
    if (requests >= maxRequests) {
      timer?.cancel();
      return;
    }
    requests++;
    queryInFlight = true;
    try {
      await requestSetting();
    } catch (error) {
      // Reboots can temporarily remove the BLE control connection.
      log?.call('Consulta indisponível: $error');
    } finally {
      queryInFlight = false;
    }
  }

  try {
    // Subscribe before writing: the acknowledgement can arrive immediately.
    await writeSetting();
    timer = Timer.periodic(pollInterval, (_) => unawaited(query()));
    unawaited(query());
    await confirmed.future.timeout(
      timeout,
      onTimeout: () {
        final detail = lastState == null
            ? 'O fone não confirmou o comando LDAC.'
            : 'O fone informou LDAC ${lastState == 0x01 ? "ativado" : "desativado"}.';
        throw StateError('$detail Não foi possível confirmar a alteração.');
      },
    );
  } finally {
    closed = true;
    timer?.cancel();
    await subscription.cancel();
  }
}
