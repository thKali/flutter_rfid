class TagRfid {
  final String epc;
  final int rssi;
  final int timestamp;

  String get barcode => epc.substring(4, 14);
  RSSIQuality get rssiQuality => switch (rssi) {
    > -50 => RSSIQuality.excellent,
    > -70 => RSSIQuality.good,
    > -90 => RSSIQuality.weak,
    _ => RSSIQuality.poor,
  };

  TagRfid({required this.epc, required this.rssi, required this.timestamp});

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TagRfid && runtimeType == other.runtimeType && epc == other.epc;

  @override
  int get hashCode => epc.hashCode;
}

enum RSSIQuality {
  excellent,
  good,
  weak,
  poor,
}