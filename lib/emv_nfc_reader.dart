import 'emv_nfc_reader_platform_interface.dart';

class EmvNfcReader {
  Future<String?> getPlatformVersion() {
    return EmvNfcReaderPlatform.instance.getPlatformVersion();
  }

  Future<Map<String, String>?> startReading() {
    return EmvNfcReaderPlatform.instance.startReading();
  }

  Future<void> stopReading() {
    return EmvNfcReaderPlatform.instance.stopReading();
  }
}