import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'emv_nfc_reader_method_channel.dart';

abstract class EmvNfcReaderPlatform extends PlatformInterface {
  /// Constructs a EmvNfcReaderPlatform.
  EmvNfcReaderPlatform() : super(token: _token);

  static final Object _token = Object();

  static EmvNfcReaderPlatform _instance = MethodChannelEmvNfcReader();

  /// The default instance of [EmvNfcReaderPlatform] to use.
  ///
  /// Defaults to [MethodChannelEmvNfcReader].
  static EmvNfcReaderPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [EmvNfcReaderPlatform] when
  /// they register themselves.
  static set instance(EmvNfcReaderPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<Map<String, String>?> startReading() {
    throw UnimplementedError('startReading() has not been implemented.');
  }

  Future<void> stopReading() {
    throw UnimplementedError('stopReading() has not been implemented.');
  }
}
