import 'package:flutter_test/flutter_test.dart';
import 'package:emv_nfc_reader/emv_nfc_reader.dart';
import 'package:emv_nfc_reader/emv_nfc_reader_platform_interface.dart';
import 'package:emv_nfc_reader/emv_nfc_reader_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockEmvNfcReaderPlatform
    with MockPlatformInterfaceMixin
    implements EmvNfcReaderPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<Map<String, String>?> startReading() =>
      Future.value({'pan': '1234123412341234'});

  @override
  Future<void> stopReading() => Future.value();
}

void main() {
  final EmvNfcReaderPlatform initialPlatform = EmvNfcReaderPlatform.instance;

  test('$MethodChannelEmvNfcReader is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelEmvNfcReader>());
  });

  test('getPlatformVersion', () async {
    EmvNfcReader emvNfcReaderPlugin = EmvNfcReader();
    MockEmvNfcReaderPlatform fakePlatform = MockEmvNfcReaderPlatform();
    EmvNfcReaderPlatform.instance = fakePlatform;

    expect(await emvNfcReaderPlugin.getPlatformVersion(), '42');
  });
}
