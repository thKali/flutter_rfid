sealed class RfidEvent {
  final int timestamp;
  RfidEvent({required this.timestamp});

  static RfidEvent fromJson(Map json) {
    return switch (json['type']) {
      'tag' => TagRfidEvent.fromJson(json),
      'connection' => ConnectionRfidEvent.fromJson(json),
      'trigger' => TriggerRfidEvent.fromJson(json),
      'inventory' => InventoryRfidEvent.fromJson(json),
      _ => throw UnimplementedError(),
    };
  }
}

class TagRfidEvent extends RfidEvent {
  TagRfidEvent({
    required super.timestamp,
    required this.epc,
    required this.rssi,
  });

  final String epc;
  final int rssi;

  factory TagRfidEvent.fromJson(Map json) {
    return TagRfidEvent(
      timestamp: json['timestamp'],
      epc: json['data']['epc'],
      rssi: json['data']['rssi'],
    );
  }
}

class ConnectionRfidEvent extends RfidEvent {
  ConnectionRfidEvent({
    required super.timestamp,
    required this.status,
    required this.readerName,
    required this.batteryLevel,
  });

  final String status;
  final String readerName;
  final int batteryLevel;

  factory ConnectionRfidEvent.fromJson(Map json) {
    return ConnectionRfidEvent(
      timestamp: json['timestamp'],
      status: json['data']['status'],
      readerName: json['data']['readerName'] ?? '',
      batteryLevel: json['data']['batteryLevel'] ?? 0,
    );
  }
}

class TriggerRfidEvent extends RfidEvent {
  TriggerRfidEvent({
    required super.timestamp,
    required this.state,
    required this.mode,
    required this.isPressing,
  });

  final String state;
  final TriggerMode mode;
  final bool isPressing;

  factory TriggerRfidEvent.fromJson(Map json) {
    return TriggerRfidEvent(
      timestamp: json['timestamp'],
      state: json['data']['state'],
      mode: TriggerMode.values.firstWhere(
        (e) => e.name == json['data']['mode'],
        orElse: () => TriggerMode.none,
      ),
      isPressing: json['data']['isPressing'],
    );
  }
}

enum TriggerMode { single, double, none }

class InventoryRfidEvent extends RfidEvent {
  InventoryRfidEvent({
    required super.timestamp,
    required this.status,
    required this.isRunning,
  });

  final String status;
  final bool isRunning;

  factory InventoryRfidEvent.fromJson(Map json) {
    return InventoryRfidEvent(
      timestamp: json['timestamp'],
      status: json['data']['status'],
      isRunning: json['data']['isRunning'],
    );
  }
}
