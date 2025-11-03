import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_coletor/src/domain/rfid_event.dart';
import 'package:flutter_coletor/src/domain/status.dart';
import 'package:flutter_coletor/src/domain/tag_rfid.dart';
export 'package:flutter_coletor/src/domain/status.dart';

class RfidController extends ChangeNotifier {
  static const platform = MethodChannel('flutter_coletor/rfid');

  final ValueNotifier<RfidControllerStatus> _status = ValueNotifier(
    RfidControllerIdle(),
  );

  final ValueNotifier<List<TagRfid>> _tags = ValueNotifier(<TagRfid>[]);
  final ValueNotifier<Set<TagRfid>> _uniqueTags = ValueNotifier(<TagRfid>{});

  final ValueNotifier<bool> _isUniqueTagsMode = ValueNotifier(true);
  ValueNotifier<bool> get isUniqueTagsMode => _isUniqueTagsMode;

  final ValueNotifier<int> _minimalRssi = ValueNotifier(-90);
  ValueNotifier<int> get minimalRssi => _minimalRssi;

  void increaseMinimalRssi() {
    _minimalRssi.value = _minimalRssi.value + 10;
  }

  void decreaseMinimalRssi() {
    _minimalRssi.value = _minimalRssi.value - 10;
  }

  clearTags() {
    _tags.value = <TagRfid>[];
    _uniqueTags.value = <TagRfid>{};
  }

  void toggleUniqueTagsMode() {
    _isUniqueTagsMode.value = !_isUniqueTagsMode.value;
  }

  ValueNotifier<List<TagRfid>> get tags => _tags;
  ValueNotifier<Set<TagRfid>> get uniqueTags => _uniqueTags;

  ValueNotifier<RfidControllerStatus> get status => _status;

  final ValueNotifier<bool> _isInventorying = ValueNotifier(false);
  ValueNotifier<bool> get isInventorying => _isInventorying;

  RfidController() {
    _setupMethodCallHandler();
    _initializeAndCheck();
  }

  Future<void> _initializeAndCheck() async {
    try {
      await platform.invokeMethod('initialize');

      //channel delay
      await Future.delayed(const Duration(milliseconds: 100));

      await platform.invokeMethod('checkConnection');
    } catch (e) {
      debugPrint('Erro ao inicializar RFID: $e');
    }
  }

  void _setupMethodCallHandler() {
    platform.setMethodCallHandler((call) async {
      if (call.method != 'onRfidEvent') {
        print('method: ${call.method}');
        print('args: ${call.arguments}');
        return;
      }

      final event = RfidEvent.fromJson(call.arguments);
      _processEvent(event);
    });
  }

  @override
  void dispose() {
    platform.setMethodCallHandler(null);
    super.dispose();
  }

  void _processEvent(RfidEvent event) {
    switch (event) {
      case TagRfidEvent():
        if (event.rssi < _minimalRssi.value) {
          return;
        }

        debugPrint('Tag: ${event.epc} - RSSI: ${event.rssi}');
        _tags.value = [
          ..._tags.value,
          TagRfid(
            epc: event.epc,
            rssi: event.rssi,
            timestamp: event.timestamp, //
          ),
        ];
        _uniqueTags.value = {
          ..._uniqueTags.value,
          TagRfid(
            epc: event.epc,
            rssi: event.rssi,
            timestamp: event.timestamp, //
          ),
        };

        break;
      case ConnectionRfidEvent():
        debugPrint('Conexão: ${event}');

        if (event.status == 'connected') {
          _status.value = RfidControllerConnected(
            readerName: event.readerName,
            batteryLevel: event.batteryLevel,
          );
        } else if (event.status == 'disconnected') {
          _status.value = RfidControllerDisconnected();
        }

        break;
      case TriggerRfidEvent():
        debugPrint(
          'Gatilho: ${event.state} - ${event.mode} - ${event.isPressing}',
        );
        if (event.mode == TriggerMode.single) {
          if (_isInventorying.value) {
            stopInventory().catchError((e) {
              debugPrint('Erro ao parar inventário via trigger: $e');
            });
          } else {
            startInventory().catchError((e) {
              debugPrint('Erro ao iniciar inventário via trigger: $e');
            });
          }
        }

        if (event.mode == TriggerMode.double) {
          trySingleRead();
        }
        break;
      case InventoryRfidEvent():
        debugPrint('Inventário: ${event.status} - Running: ${event.isRunning}');
        _isInventorying.value = event.isRunning;
        break;
    }
  }

  Future<void> connect() async {
    _status.value = RfidControllerConnecting();
    try {
      await platform.invokeMethod('connect');
    } catch (e) {
      debugPrint('Erro ao conectar: $e');
      _status.value = RfidControllerError(message: e.toString());
      rethrow;
    }
  }

  Future<void> disconnect() async {
    _status.value = RfidControllerDisconnecting();
    try {
      await platform.invokeMethod('disconnect');
    } catch (e) {
      debugPrint('Erro ao desconectar: $e');
      _status.value = RfidControllerError(message: e.toString());
      rethrow;
    }
  }

  Future<void> startInventory() async {
    try {
      await platform.invokeMethod('startInventory');
    } catch (e) {
      debugPrint('Erro ao iniciar inventário: $e');
      rethrow;
    }
  }

  Future<void> stopInventory() async {
    try {
      await platform.invokeMethod('stopInventory');
    } catch (e) {
      debugPrint('Erro ao parar inventário: $e');
      rethrow;
    }
  }

  Future<void> trySingleRead() async {
    if (_isInventorying.value) {
      return;
    }

    startInventory();
    await Future.delayed(const Duration(milliseconds: 1000));
    stopInventory();
  }
}
