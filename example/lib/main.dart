import 'dart:developer';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:emv_nfc_reader/emv_nfc_reader.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _status = 'Ready to scan';
  Map<String, String>? _cardData;
  bool _isScanning = false;
  final _emvNfcReaderPlugin = EmvNfcReader();

  Future<void> _startScan() async {
    setState(() {
      _isScanning = true;
      _status = 'Approach an EMV card to the back of the phone';
      _cardData = null;
    });

    try {
      final result = await _emvNfcReaderPlugin.startReading();
      setState(() {
        _cardData = result;
        _status = result != null
            ? 'Card read successfully'
            : 'Failed to read card';
      });
    } catch (e) {
      setState(() {
        _status = 'Error: $e';
        log(e.toString());
      });
    } finally {
      setState(() {
        _isScanning = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark().copyWith(
        primaryColor: Colors.blueAccent,
        scaffoldBackgroundColor: const Color(0xFF121212),
      ),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('EMV NFC Reader Pro'),
          backgroundColor: Colors.transparent,
          elevation: 0,
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (_cardData != null) ...[
                _buildCreditCard(),
                const SizedBox(height: 24),
                _buildDataSection('Cardholder Info', [
                  _buildDataRow('Name', _cardData!['cardholder'] ?? 'N/A'),
                  _buildDataRow(
                    'Preferred Name',
                    _cardData!['preferredName'] ?? 'N/A',
                  ),
                  _buildDataRow('Language', _cardData!['language'] ?? 'N/A'),
                  _buildDataRow('Country Code', _cardData!['country'] ?? 'N/A'),
                ]),
                const SizedBox(height: 16),
                _buildDataSection('Bank Details', [
                  _buildDataRow('IBAN', _cardData!['iban'] ?? 'N/A'),
                  _buildDataRow('BIC', _cardData!['bic'] ?? 'N/A'),
                  _buildDataRow(
                    'PAN Seq Num',
                    _cardData!['panSequenceNumber'] ?? 'N/A',
                  ),
                  _buildDataRow('App Label', _cardData!['label'] ?? 'N/A'),
                ]),
                const SizedBox(height: 16),
                _buildDataSection('Security & Proprietary', [
                  _buildDataRow(
                    'Transaction Counter (ATC)',
                    _cardData!['atc'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'Last Online ATC',
                    _cardData!['lastOnlineAtc'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'PIN Tries Remaining',
                    _cardData!['pinTry'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'Form Factor',
                    _cardData!['formFactor'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'Offline Balance',
                    _cardData!['offlineBalance'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'ADA (Default Action)',
                    _cardData!['applicationDefaultAction'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'Card Qualifiers (CTQ)',
                    _cardData!['cardTransactionQualifiers'] ?? 'N/A',
                  ),
                  _buildDataRow(
                    'Issuer Data',
                    _cardData!['issuerData'] ?? 'N/A',
                  ),
                ]),
                if (_cardData!['transactions'] != null) ...[
                  const SizedBox(height: 16),
                  _buildDataSection('Transaction History', [
                    ..._cardData!['transactions']!
                        .split('|')
                        .map(
                          (tx) => Padding(
                            padding: const EdgeInsets.only(bottom: 8.0),
                            child: Text(
                              tx,
                              style: const TextStyle(
                                fontSize: 11,
                                fontFamily: 'monospace',
                                color: Colors.greenAccent,
                              ),
                            ),
                          ),
                        )
                        .toList(),
                  ]),
                ],
              ] else
                _buildScanPlaceholder(),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: _isScanning ? null : _startScan,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blueAccent,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(_isScanning ? 'SCANNING...' : 'SCAN CARD'),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCreditCard() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF1A237E), Color(0xFF3949AB)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.blue.withOpacity(0.2),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _cardData!['label']?.toUpperCase() ?? 'CREDIT CARD',
                style: const TextStyle(
                  letterSpacing: 2,
                  fontSize: 12,
                  color: Colors.white70,
                ),
              ),
              const Icon(Icons.nfc, color: Colors.white54),
            ],
          ),
          const SizedBox(height: 24),
          Text(
            _cardData!['pan'] ?? '**** **** **** ****',
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              letterSpacing: 4,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'EXPIRES',
                    style: TextStyle(fontSize: 10, color: Colors.white70),
                  ),
                  Text(
                    _cardData!['expiry'] ?? '--/--',
                    style: const TextStyle(color: Colors.white),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Text(
                    'HOLDER',
                    style: TextStyle(fontSize: 10, color: Colors.white70),
                  ),
                  Text(
                    _cardData!['cardholder'] ?? 'VALUED CUSTOMER',
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildDataSection(String title, List<Widget> children) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              color: Colors.blueAccent,
            ),
          ),
          const Divider(color: Colors.white10),
          ...children,
        ],
      ),
    );
  }

  Widget _buildDataRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(color: Colors.white54, fontSize: 12),
          ),
          Flexible(
            child: Text(
              value,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                overflow: TextOverflow.ellipsis,
              ),
              textAlign: TextAlign.end,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildScanPlaceholder() {
    return Container(
      height: 200,
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white10),
      ),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.nfc,
              size: 48,
              color: _isScanning ? Colors.blueAccent : Colors.white24,
            ),
            const SizedBox(height: 16),
            Text(
              _status,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54),
            ),
          ],
        ),
      ),
    );
  }
}
