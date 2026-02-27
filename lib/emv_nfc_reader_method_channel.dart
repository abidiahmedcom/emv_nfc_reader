import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'emv_nfc_reader_platform_interface.dart';

/// An implementation of [EmvNfcReaderPlatform] that uses method channels.
class MethodChannelEmvNfcReader extends EmvNfcReaderPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('emv_nfc_reader');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<Map<String, String>?> startReading() async {
    return await methodChannel.invokeMapMethod<String, String>('startReading');
  }

  @override
  Future<void> stopReading() async {
    await methodChannel.invokeMethod<void>('stopReading');
  }
}
