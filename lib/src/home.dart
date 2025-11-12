import 'package:flutter/material.dart';
import 'package:flutter_coletor/src/rfid_controller.dart';
import 'package:flutter_coletor/src/domain/tag_rfid.dart';

class RfidHomePage extends StatefulWidget {
  const RfidHomePage({super.key});

  @override
  State<RfidHomePage> createState() => _RfidHomePageState();
}

class _RfidHomePageState extends State<RfidHomePage> {
  late final RfidController _controller;

  @override
  void initState() {
    super.initState();
    _controller = RfidController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('POC COLETOR'),
        centerTitle: true,
        elevation: 2,
      ),
      body: ValueListenableBuilder(
        valueListenable: _controller.status,
        builder: (context, status, child) => switch (status) {
          RfidControllerConnecting() ||
          RfidControllerDisconnecting() => const Center(
            child: CircularProgressIndicator(), //
          ),
          RfidControllerIdle() || RfidControllerDisconnected() => Center(
            child: Column(
              children: [
                ElevatedButton(
                  onPressed: () async {
                    try {
                      await _controller.connect();
                    } catch (e) {
                      // Erro já foi tratado no controller
                    }
                  },
                  child: const Text('Conectar coletor'),
                ),
              ],
            ),
          ),
          RfidControllerError() => Center(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.error_outline,
                    size: 64,
                    color: Colors.red,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    status.message,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 14),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton(
                    onPressed: () async {
                      try {
                        await _controller.connect();
                      } catch (e) {
                        // Erro já foi tratado no controller
                      }
                    },
                    child: const Text('Tentar novamente'),
                  ),
                ],
              ),
            ),
          ),
          RfidControllerConnected() => Center(
            child: Column(
              children: [
                ElevatedButton(
                  onPressed: () async {
                    await _controller.disconnect();
                  },
                  child: const Text('Desconectar coletor'),
                ),
                Text('Leitor: ${status.readerName}'),
                Text('Bateria: ${status.batteryLevel}%'),

                ListenableBuilder(
                  //merge isinventoruying and tags
                  listenable: Listenable.merge([
                    _controller.isInventorying,
                    _controller.tags,
                    _controller.isUniqueTagsMode,
                    _controller.minimalRssi,
                  ]),
                  builder: (context, child) {
                    final tags = _controller.isUniqueTagsMode.value
                        ? _controller.uniqueTags.value.toList()
                        : _controller.tags.value.reversed.toList();
                    final isInventorying = _controller.isInventorying.value;
                    final minimalRssi = _controller.minimalRssi.value;

                    return Expanded(
                      child: ListView.builder(
                        itemCount: tags.length + 1,
                        itemBuilder: (context, index) {
                          if (index == 0) {
                            return Row(
                              children: [
                                Text('Tags: ${tags.length}'),
                                IconButton(
                                  onPressed: _controller.clearTags,
                                  icon: const Icon(Icons.delete),
                                ),
                                IconButton(
                                  onPressed: () async {
                                    if (isInventorying) {
                                      await _controller.stopInventory();
                                    } else {
                                      await _controller.startInventory();
                                    }
                                  },
                                  icon: Icon(
                                    isInventorying
                                        ? Icons.circle
                                        : Icons.circle_outlined,
                                    color: isInventorying
                                        ? Colors.green
                                        : Colors.red,
                                  ),
                                ),
                                TextButton(
                                  onPressed: () {
                                    showDialog(
                                      context: context,
                                      builder: (context) => AlertDialog(
                                        title: Text('Minimal RSSI'),
                                        actions: [
                                          TextButton(
                                            onPressed:
                                                _controller.decreaseMinimalRssi,
                                            child: Text('Decrease'),
                                          ),
                                          TextButton(
                                            onPressed:
                                                _controller.increaseMinimalRssi,
                                            child: Text('Increase'),
                                          ),
                                        ],
                                      ),
                                    );
                                  },
                                  child: Text('$minimalRssi dBm'),
                                ),
                                Text('Unique Tags'),
                                Switch(
                                  value: _controller.isUniqueTagsMode.value,
                                  onChanged: (value) {
                                    _controller.toggleUniqueTagsMode();
                                  },
                                ),
                              ],
                            );
                          }

                          final tag = tags[index - 1];

                          return Card(
                            child: ListTile(
                              title: Text(tag.barcode),
                              subtitle: Text('RSSI: ${tag.rssi}'),
                              trailing: Icon(
                                Icons.signal_cellular_alt,
                                color: switch (tag.rssiQuality) {
                                  RSSIQuality.excellent => Colors.green,
                                  RSSIQuality.good => Colors.orange,
                                  RSSIQuality.weak => Colors.red,
                                  RSSIQuality.poor => Colors.black,
                                },
                              ),
                            ),
                          );
                        },
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        },
      ),
    );
  }
}
