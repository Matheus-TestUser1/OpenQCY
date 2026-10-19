import 'package:flutter/material.dart';

import '../core/qcy/anc.dart';

class AncSelector extends StatefulWidget {
  const AncSelector({
    super.key,
    required this.selected,
    required this.onSelected,
    this.level,
    this.busy = false,
  });

  final AncMode? selected;
  final int? level;
  final void Function(AncMode mode, [int? level]) onSelected;
  final bool busy;

  @override
  State<AncSelector> createState() => _AncSelectorState();
}

class _AncSelectorState extends State<AncSelector> {
  static const _modes = AncMode.values;
  double? _drag;

  AncMode? get selected => widget.selected;
  bool get busy => widget.busy;
  ValueChanged<AncMode> get onSelected => widget.onSelected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 280),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      child: busy
          ? const Padding(
              key: ValueKey('busy'),
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            )
          : Column(
              key: ValueKey(selected),
              mainAxisSize: MainAxisSize.min,
              children: [
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  alignment: WrapAlignment.center,
                  children: [
                    for (final mode in _modes)
                      FilterChip(
                        selected: selected == mode,
                        showCheckmark: true,
                        avatar: Icon(_iconFor(mode), size: 18),
                        label: Text(mode.label),
                        onSelected: busy ? null : (_) => onSelected(mode),
                        selectedColor: scheme.primaryContainer,
                        checkmarkColor: scheme.onPrimaryContainer,
                      ),
                  ],
                ),
                if (selected != null && selected!.levels > 1)
                  _levelSlider(selected!),
              ],
            ),
    );
  }

  Widget _levelSlider(AncMode mode) {
    final max = mode.levels - 1;
    final value = (_drag ?? (widget.level ?? mode.defaultLevel).toDouble())
        .clamp(0, max)
        .toDouble();
    return Column(
      children: [
        const SizedBox(height: 12),
        Text('Level ${value.round() + 1} of ${mode.levels}'),
        Slider(
          value: value,
          max: max.toDouble(),
          divisions: max,
          onChanged: (v) => setState(() => _drag = v),
          onChangeEnd: (v) {
            setState(() => _drag = null);
            widget.onSelected(mode, v.round());
          },
        ),
      ],
    );
  }

  IconData _iconFor(AncMode mode) => switch (mode) {
        AncMode.off => Icons.hearing_disabled,
        AncMode.indoor => Icons.noise_aware,
        AncMode.commuting => Icons.directions_transit,
        AncMode.transparency => Icons.hearing,
        AncMode.adaptive => Icons.auto_mode,
        AncMode.noisy => Icons.volume_up,
        AncMode.antiWind => Icons.air,
      };
}
