package com.antigravity.emv.emv_nfc_reader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.util.*
import android.util.Log
import java.text.SimpleDateFormat

class EmvCardReader(private val tag: Tag) {

    private val TAG = "EmvCardReader"

    fun readCard(): Map<String, String>? {
        val isoDep = IsoDep.get(tag) ?: return null
        try {
            isoDep.connect()
            isoDep.timeout = 5000
            Log.d(TAG, "Connected to card. Max transceive length: ${isoDep.maxTransceiveLength}")

            // 1. Select PPSE to find AIDs
            Log.d(TAG, "Selecting PPSE...")
            val ppseRes = transceive(isoDep, "00A404000E325041592E5359532E444446303100")
            val aids = if (ppseRes != null && isSuccess(ppseRes)) {
                Log.d(TAG, "PPSE Select Success: ${toHex(ppseRes)}")
                extractAids(ppseRes)
            } else {
                Log.d(TAG, "PPSE Select Failed or Not Found. Using fallback AIDs.")
                listOf("A0000000031010", "A0000000041010", "A0000000032010", "A0000000043060", "A0000000046000") 
            }

            Log.d(TAG, "AIDs to try: $aids")

            for (aid in aids) {
                val aidBytesLen = aid.length / 2
                val selectCmd = "00A40400${String.format("%02X", aidBytesLen)}${aid}00"
                Log.d(TAG, "Selecting AID $aid: $selectCmd")
                val selectRes = transceive(isoDep, selectCmd)
                
                if (selectRes != null && isSuccess(selectRes)) {
                    Log.d(TAG, "AID Select Success: ${toHex(selectRes)}")
                    val cardData = mutableMapOf<String, String>()
                    
                    // Parse Select response
                    parseCardData(selectRes, cardData)
                    
                    // 3. Handle GPO (Get Processing Options)
                    val afl = performGpo(isoDep, selectRes, cardData) 
                    
                    if (afl != null) {
                        Log.d(TAG, "AFL found: ${toHex(afl)}. Reading records...")
                        readAflRecords(isoDep, afl, cardData)
                    }

                    // Try to fetch missing data via GET DATA command
                    tryGetData(isoDep, cardData)

                    // Extract Transaction History if available
                    extractTransactionHistory(isoDep, cardData)

                    if (cardData.containsKey("pan")) {
                        Log.d(TAG, "Successfully extracted data for AID $aid")
                        return cardData
                    } else {
                        Log.d(TAG, "No PAN found for AID $aid")
                    }
                } else {
                    Log.d(TAG, "AID Select Failed for $aid")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in readCard", e)
        } finally {
            try { isoDep.close() } catch (e: Exception) {}
        }
        return null
    }

    private fun performGpo(isoDep: IsoDep, selectRes: ByteArray, cardData: MutableMap<String, String>): ByteArray? {
        val tlv = TlvNode.parse(selectRes) ?: return null
        val pdol = tlv.find(0x9F38)?.value ?: byteArrayOf()
        Log.d(TAG, "PDOL found: ${toHex(pdol)}")
        
        val pdolData = fillPdol(pdol)
        Log.d(TAG, "PDOL filled data: ${toHex(pdolData)}")

        val gpoData = mutableListOf<Byte>()
        gpoData.add(0x83.toByte())
        if (pdolData.size > 0x7F) {
            gpoData.add(0x81.toByte())
        }
        gpoData.add(pdolData.size.toByte())
        pdolData.forEach { gpoData.add(it) }
        
        val gpoCmd = "80A80000${String.format("%02X", gpoData.size)}${toHex(gpoData.toByteArray())}00"
        Log.d(TAG, "Sending GPO: $gpoCmd")
        val gpoRes = transceive(isoDep, gpoCmd) ?: return null
        Log.d(TAG, "GPO Response: ${toHex(gpoRes)}")
        
        if (!isSuccess(gpoRes)) {
            if (pdolData.isEmpty()) {
                 val fallbackGpo = "80A8000002830000"
                 Log.d(TAG, "Retrying GPO with empty 8300: $fallbackGpo")
                 val fallbackRes = transceive(isoDep, fallbackGpo)
                 if (fallbackRes != null && isSuccess(fallbackRes)) {
                     parseCardData(fallbackRes, cardData)
                     return extractAfl(fallbackRes)
                 }
            }
            return null
        }
        
        parseCardData(gpoRes, cardData)
        return extractAfl(gpoRes)
    }

    private fun extractAfl(gpoRes: ByteArray): ByteArray? {
        if (gpoRes[0] == 0x80.toByte()) {
            return if (gpoRes.size > 4) gpoRes.copyOfRange(4, gpoRes.size - 2) else null
        } else if (gpoRes[0] == 0x77.toByte()) {
            val resTlv = TlvNode.parse(gpoRes)
            return resTlv?.find(0x94)?.value
        }
        return null
    }

    private fun fillPdol(pdol: ByteArray): ByteArray {
        val tags = parsePdolTags(pdol)
        val out = mutableListOf<Byte>()
        for (tag in tags) {
            val value = getTerminalValue(tag.tag, tag.length)
            value.forEach { out.add(it) }
        }
        return out.toByteArray()
    }

    private fun getTerminalValue(tag: Int, length: Int): ByteArray {
        val res = when (tag) {
            0x9F66 -> hexToBytes("F620C000") // Terminal Transaction Qualifiers
            0x9F1A -> hexToBytes("0250") // France 
            0x5F2A -> hexToBytes("0978") // EUR
            0x9A -> hexToBytes(SimpleDateFormat("yyMMdd").format(Date()))
            0x9C -> hexToBytes("00") // Purchase
            0x9F37 -> {
                val b = ByteArray(4)
                Random().nextBytes(b)
                b
            }
            0x9F33 -> hexToBytes("E0A000") // Terminal Capabilities
            0x9F40 -> hexToBytes("8E00B05005") 
            0x9F1E -> hexToBytes("3031323334353637") 
            0x9F35 -> hexToBytes("22") // Terminal Type
            else -> ByteArray(length)
        }
        return if (res.size > length) res.copyOfRange(0, length) 
               else if (res.size < length) res + ByteArray(length - res.size)
               else res
    }

    private fun readAflRecords(isoDep: IsoDep, afl: ByteArray, cardData: MutableMap<String, String>) {
        if (afl.isEmpty()) return
        for (i in 0 until afl.size step 4) {
            if (i + 4 > afl.size) break
            val sfi = afl[i].toInt() shr 3
            val first = afl[i+1].toInt() and 0xFF
            val last = afl[i+2].toInt() and 0xFF
            
            for (rec in first..last) {
                val p2 = (sfi shl 3) or 4
                val cmd = "00B2${String.format("%02X", rec)}${String.format("%02X", p2)}00"
                val res = transceive(isoDep, cmd)
                if (res != null && isSuccess(res)) {
                    parseCardData(res, cardData)
                }
            }
        }
    }

    private fun tryGetData(isoDep: IsoDep, cardData: MutableMap<String, String>) {
        val tagsToTry = listOf(
            0x9F17, // PIN Try Counter
            0x9F13, // Last Online ATC
            0x9F36, // ATC
            0x5F50, // Issuer URL
            0x5F53, // IBAN
            0x5F54, // BIC
            0x9F4F, // Log Format
            0x5F34, // PAN Sequence Number
            0x9F6E, // Form Factor Indicator
            0x9F6D, // Merchant Identifier / Device Info
            0x9F5D, // Available Offline Spending Amount
            0x9F52, // Application Default Action
            0x9F5B, // Issuer Script Results
            0x9F10, // Issuer Application Data
            0x5F2D, // Language Preference
            0x5F42  // Application Currency Code
        )
        
        for (tag in tagsToTry) {
            val hexTag = String.format("%04X", tag)
            val cmd = "80CA${hexTag}00"
            val res = transceive(isoDep, cmd)
            if (res != null && isSuccess(res)) {
                Log.d(TAG, "GET DATA $hexTag Success: ${toHex(res)}")
                parseCardData(res, cardData)
            }
        }
    }

    private fun parseCardData(data: ByteArray, cardData: MutableMap<String, String>) {
        if (data.size < 2) return
        val nodes = TlvNode.parseRecursive(data, 0, data.size - 2)
        for (node in nodes) {
            lookForData(node, cardData)
        }
    }
    
    private fun lookForData(node: TlvNode, cardData: MutableMap<String, String>) {
        when (node.tag) {
            0x5A -> cardData["pan"] = formatPan(toHex(node.value).trimEnd('F', 'f'))
            0x5F24 -> cardData["expiry"] = formatExpiry(toHex(node.value))
            0x5F20 -> cardData["cardholder"] = String(node.value).trim()
            0x5F53 -> cardData["iban"] = String(node.value).trim()
            0x5F54 -> cardData["bic"] = String(node.value).trim()
            0x5F2D -> cardData["language"] = String(node.value).trim()
            0x5F28 -> cardData["country"] = toHex(node.value)
            0x9F36 -> cardData["atc"] = toHex(node.value).toInt(16).toString()
            0x9F17 -> cardData["pinTry"] = toHex(node.value).toInt(16).toString()
            0x9F13 -> cardData["lastOnlineAtc"] = toHex(node.value).toInt(16).toString()
            0x9F12 -> cardData["preferredName"] = String(node.value).trim()
            0x50 -> cardData["label"] = String(node.value).trim()
            0x9F4D -> cardData["logEntry"] = toHex(node.value) 
            0x9F4F -> cardData["logFormat"] = toHex(node.value)
            0x5F34 -> cardData["panSequenceNumber"] = toHex(node.value)
            0x9F6E -> cardData["formFactor"] = toHex(node.value)
            0x9F10 -> cardData["issuerData"] = toHex(node.value)
            0x5F42 -> cardData["currencyCode"] = toHex(node.value)
            0x9F5D -> cardData["offlineBalance"] = toHex(node.value)
            0x9F52 -> cardData["applicationDefaultAction"] = toHex(node.value)
            0x9F6C -> cardData["cardTransactionQualifiers"] = toHex(node.value)
            0x57 -> { // Track 2
                val track2 = toHex(node.value)
                val dIndex = track2.indexOf('D')
                if (dIndex != -1) {
                    if (!cardData.containsKey("pan")) {
                        cardData["pan"] = formatPan(track2.substring(0, dIndex).trimEnd('F', 'f'))
                    }
                    if (!cardData.containsKey("expiry") && track2.length >= dIndex + 5) {
                        val expiry = track2.substring(dIndex + 1, dIndex + 5)
                        cardData["expiry"] = "${expiry.substring(2, 4)}/${expiry.substring(0, 2)}"
                    }
                }
            }
            0x56 -> { // Track 1
                try {
                    val track1 = String(node.value)
                    val caret1 = track1.indexOf('^')
                    val caret2 = track1.indexOf('^', caret1 + 1)
                    if (caret1 != -1 && caret2 != -1) {
                        val name = track1.substring(caret1 + 1, caret2).trim()
                        if (name.isNotEmpty()) cardData["cardholder"] = name
                    }
                } catch (e: Exception) {}
            }
        }
        for (child in node.children) {
            lookForData(child, cardData)
        }
    }

    private fun extractTransactionHistory(isoDep: IsoDep, cardData: MutableMap<String, String>) {
        val logEntryHex = cardData["logEntry"] ?: return
        val logFormatHex = cardData["logFormat"] ?: ""
        
        try {
            val logEntry = hexToBytes(logEntryHex)
            if (logEntry.size < 2) return
            val sfi = logEntry[0].toInt()
            val numRecs = logEntry[1].toInt() and 0xFF
            
            val logFormatList = if (logFormatHex.isNotEmpty()) parseLogFormat(hexToBytes(logFormatHex)) else listOf()
            Log.d(TAG, "Parsing transactions for SFI $sfi, records: $numRecs")

            val transactions = mutableListOf<String>()
            for (rec in 1..numRecs) {
                val p2 = (sfi shl 3) or 4
                val cmd = "00B2${String.format("%02X", rec)}${String.format("%02X", p2)}00"
                val res = transceive(isoDep, cmd)
                if (res != null && isSuccess(res)) {
                    val recordData = res.copyOfRange(0, res.size - 2)
                    if (logFormatList.isNotEmpty()) {
                        transactions.add(parseTransaction(recordData, logFormatList))
                    } else {
                        transactions.add("RAW:" + toHex(recordData))
                    }
                }
            }
            if (transactions.isNotEmpty()) {
                cardData["transactions"] = transactions.joinToString("|")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading transactions", e)
        }
    }
    
    private fun parseLogFormat(data: ByteArray): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < data.size) {
            var tag = data[i].toInt() and 0xFF
            i++
            if (tag and 0x1F == 0x1F) {
                tag = (tag shl 8) or (data[i].toInt() and 0xFF)
                i++
            }
            if (i >= data.size) break
            val len = data[i].toInt() and 0xFF
            i++
            list.add(Pair(tag, len))
        }
        return list
    }

    private fun parseTransaction(data: ByteArray, format: List<Pair<Int, Int>>): String {
        var offset = 0
        val items = mutableListOf<String>()
        for (item in format) {
            val tag = item.first
            val len = item.second
            if (offset + len > data.size) break
            val value = data.copyOfRange(offset, offset + len)
            when (tag) {
                0x9A -> items.add("Date: " + toHex(value).chunked(2).joinToString("/"))
                0x9F21 -> items.add("Time: " + toHex(value).chunked(2).joinToString(":"))
                0x9F02 -> items.add("Amt: " + toHex(value).toLongOrNull()?.toString()?.padStart(2, '0')?.let { it.dropLast(2) + "." + it.takeLast(2) } ?: toHex(value))
                0x5F2A -> items.add("Cur: " + toHex(value))
                0x9C -> items.add("Type: " + toHex(value))
            }
            offset += len
        }
        return items.joinToString(", ")
    }

    private fun formatPan(pan: String): String = pan.chunked(4).joinToString(" ")
    
    private fun formatExpiry(hex: String): String {
        return if (hex.length >= 4) "${hex.substring(2, 4)}/${hex.substring(0, 2)}" else hex
    }

    private fun transceive(isoDep: IsoDep, hex: String): ByteArray? {
        return try { isoDep.transceive(hexToBytes(hex)) } catch (e: Exception) { null }
    }

    private fun isSuccess(res: ByteArray): Boolean {
        if (res.size < 2) return false
        val sw1 = res[res.size - 2].toInt() and 0xFF
        val sw2 = res[res.size - 1].toInt() and 0xFF
        return (sw1 == 0x90 && sw2 == 0x00) || sw1 == 0x91
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.replace(" ", "")
        val data = ByteArray(s.length / 2)
        for (i in 0 until s.length step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    private fun extractAids(res: ByteArray): List<String> {
        val aids = mutableListOf<String>()
        TlvNode.parse(res)?.findAll(0x4F)?.forEach { aids.add(toHex(it.value)) }
        return aids
    }

    private fun parsePdolTags(pdol: ByteArray): List<PdolTag> {
        val list = mutableListOf<PdolTag>()
        var i = 0
        while (i < pdol.size) {
            var tag = pdol[i].toInt() and 0xFF
            i++
            if (tag and 0x1F == 0x1F) {
                tag = (tag shl 8) or (pdol[i].toInt() and 0xFF)
                i++
            }
            if (i >= pdol.size) break
            val len = pdol[i].toInt() and 0xFF
            i++
            list.add(PdolTag(tag, len))
        }
        return list
    }

    data class PdolTag(val tag: Int, val length: Int)

    class TlvNode(val tag: Int, val length: Int, val value: ByteArray, val children: List<TlvNode> = listOf()) {
        fun find(targetTag: Int): TlvNode? {
            if (tag == targetTag) return this
            for (child in children) {
                val found = child.find(targetTag)
                if (found != null) return found
            }
            return null
        }

        fun findAll(targetTag: Int): List<TlvNode> {
            val results = mutableListOf<TlvNode>()
            if (tag == targetTag) results.add(this)
            for (child in children) results.addAll(child.findAll(targetTag))
            return results
        }
        
        companion object {
            fun parse(data: ByteArray): TlvNode? {
                if (data.size < 2) return null
                return try { parseRecursive(data, 0, data.size - 2).firstOrNull() } catch (e: Exception) { null }
            }

            fun parseRecursive(data: ByteArray, start: Int, end: Int): List<TlvNode> {
                val nodes = mutableListOf<TlvNode>()
                var i = start
                while (i < end) {
                    var tagFirstByte = data[i].toInt() and 0xFF
                    if (tagFirstByte == 0x00 || tagFirstByte == 0xFF) { i++; continue }
                    var tag = tagFirstByte
                    i++
                    if (tagFirstByte and 0x1F == 0x1F) {
                        if (i >= end) break
                        tag = (tag shl 8) or (data[i].toInt() and 0xFF)
                        if (data[i].toInt() and 0x80 != 0) { 
                             i++; if (i >= end) break
                             tag = (tag shl 8) or (data[i].toInt() and 0xFF)
                        }
                        i++
                    }
                    if (i >= end) break
                    var len = data[i].toInt() and 0xFF
                    i++
                    if (len > 0x80) {
                        val lenBytes = len and 0x7F
                        len = 0
                        for (j in 0 until lenBytes) {
                            if (i >= end) break
                            len = (len shl 8) or (data[i].toInt() and 0xFF)
                            i++
                        }
                    }
                    if (i + len > end) break
                    val value = data.copyOfRange(i, i + len)
                    val constructed = (tagFirstByte and 0x20) != 0
                    val children = if (constructed) parseRecursive(data, i, i + len) else listOf()
                    nodes.add(TlvNode(tag, len, value, children))
                    i += len
                }
                return nodes
            }
        }
    }
}
