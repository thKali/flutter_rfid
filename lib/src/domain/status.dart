sealed class RfidControllerStatus {}

class RfidControllerIdle extends RfidControllerStatus {}

class RfidControllerConnecting extends RfidControllerStatus {}

class RfidControllerConnected extends RfidControllerStatus {
  final String readerName;
  final int batteryLevel;
  RfidControllerConnected({required this.readerName, required this.batteryLevel});
}

class RfidControllerDisconnecting extends RfidControllerStatus {}

class RfidControllerDisconnected extends RfidControllerStatus {}

class RfidControllerError extends RfidControllerStatus {
  final String message;
  RfidControllerError({required this.message});
}