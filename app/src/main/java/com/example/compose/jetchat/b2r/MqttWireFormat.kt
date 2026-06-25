package com.example.compose.jetchat.b2r

import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * b2r: a minimal MQTT 3.1.1 control-packet codec over a WebSocket. Just enough to
 * use public brokers as a retained mailbox. QoS 0, clean session, no auth.
 * NOT yet validated against a live broker on a device — treat as experimental.
 */
internal object MqttWireFormat {

    private const val TYPE_CONNECT = 0x10
    private const val TYPE_PUBLISH = 0x30
    private const val TYPE_SUBSCRIBE = 0x82
    private const val TYPE_PINGREQ = 0xC0
    private const val TYPE_DISCONNECT = 0xE0

    private const val RETAIN_FLAG = 0x01
    private const val PROTOCOL_LEVEL = 0x04
    private const val CLEAN_SESSION = 0x02
    private const val BYTE_MASK = 0xFF
    private const val TYPE_MASK = 0xF0
    private const val CONTINUATION_BIT = 0x80
    private const val KEEP_ALIVE = 30

    private fun encodeRemainingLength(length: Int): ByteArray {
        val out = ArrayList<Byte>()
        var remaining = length
        do {
            var digit = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining > 0) digit = digit or CONTINUATION_BIT
            out.add(digit.toByte())
        } while (remaining > 0)
        return out.toByteArray()
    }

    private fun encodeString(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return byteArrayOf((bytes.size ushr 8).toByte(), (bytes.size and BYTE_MASK).toByte()) + bytes
    }

    private fun packet(firstByte: Int, body: ByteArray): ByteString =
        (byteArrayOf(firstByte.toByte()) + encodeRemainingLength(body.size) + body).toByteString()

    fun connect(clientId: String): ByteString {
        val variableHeader = encodeString("MQTT") +
            byteArrayOf(PROTOCOL_LEVEL.toByte()) +
            byteArrayOf(CLEAN_SESSION.toByte()) +
            byteArrayOf((KEEP_ALIVE ushr 8).toByte(), (KEEP_ALIVE and BYTE_MASK).toByte())
        return packet(TYPE_CONNECT, variableHeader + encodeString(clientId))
    }

    fun subscribe(packetId: Int, topicFilter: String): ByteString {
        val variableHeader = byteArrayOf((packetId ushr 8).toByte(), (packetId and BYTE_MASK).toByte())
        return packet(TYPE_SUBSCRIBE, variableHeader + encodeString(topicFilter) + byteArrayOf(0x00))
    }

    fun publishRetained(topic: String, payload: ByteArray): ByteString =
        packet(TYPE_PUBLISH or RETAIN_FLAG, encodeString(topic) + payload)

    fun disconnect(): ByteString = packet(TYPE_DISCONNECT, ByteArray(0))

    data class PublishMessage(val topic: String, val payload: ByteArray)

    fun parsePublish(frame: ByteString): PublishMessage? {
        val data = frame.toByteArray()
        if (data.isEmpty() || (data[0].toInt() and TYPE_MASK) != TYPE_PUBLISH) return null

        var index = 1
        while (index < data.size) {
            val encoded = data[index].toInt() and BYTE_MASK
            index++
            if (encoded and CONTINUATION_BIT == 0) break
        }
        if (index + 2 > data.size) return null

        val topicLength = ((data[index].toInt() and BYTE_MASK) shl 8) or (data[index + 1].toInt() and BYTE_MASK)
        index += 2
        if (index + topicLength > data.size) return null

        val topic = data.copyOfRange(index, index + topicLength).decodeToString()
        index += topicLength
        return PublishMessage(topic = topic, payload = data.copyOfRange(index, data.size))
    }
}
