# emv_nfc_reader

A powerful Flutter plugin for reading and extracting comprehensive data from EMV (Europay, Mastercard, and Visa) NFC cards.

## Features

- **Standard Data Extraction**: PAN (Card Number), Expiry Date.
- **Advanced Data Extraction**:
  - Cardholder Name (from tags or Track 1 fallback).
  - Bank Details (IBAN, BIC).
  - PAN Sequence Number.
- **Usage & Security Counters**:
  - Application Transaction Counter (ATC) in decimal.
  - PIN Try Counter in decimal.
  - Last Online ATC in decimal.
- **Transaction History**: Extracts and parses the card's internal transaction log (Date, Time, Amount, Currency).
- **Proprietary Data**: Fetches Issuer Application Data (IAD), Application Default Action (ADA), and Form Factor Indicator.
- **Robust Parsing**: Handles PDOL (Processing Options Data Object List) and AFL (Application File Locator) logic automatically.

## Getting Started

### Android Setup

Add the following permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="false" />
```

### Usage

```dart
import 'package:emv_nfc_reader/emv_nfc_reader.dart';

final _emvNfcReader = EmvNfcReader();

// Start scanning for a card
try {
  final Map<String, String>? cardData = await _emvNfcReader.startReading();
  if (cardData != null) {
      print('PAN: ${cardData['pan']}');
      print('Expiry: ${cardData['expiry']}');
      print('Holder: ${cardData['cardholder']}');
      print('ATC: ${cardData['atc']}');
  }
} catch (e) {
  print('Error reading card: $e');
}
```

## Data Fields

The `startReading()` method returns a `Map<String, String>` containing:

| Key            | Description                         |
| -------------- | ----------------------------------- |
| `pan`          | Formatted Card Number               |
| `expiry`       | Expiry Date (MM/YY)                 |
| `cardholder`   | Cardholder Name                     |
| `iban`         | International Bank Account Number   |
| `atc`          | Transaction Counter (Decimal)       |
| `pinTry`       | PIN Attempts Remaining (Decimal)    |
| `transactions` | Historical transactions (formatted) |
| ...            | And many more technical tags        |

## Limitations

- **Hardware Dependency**: Requires an NFC-enabled Android device.
- **Issuer Privacy**: Some banks opt-out of sharing certain fields (like IBAN or Name) via the contactless interface.
- **Security**: This library cannot extract PINs or Private Keys.

## License

MIT License.
