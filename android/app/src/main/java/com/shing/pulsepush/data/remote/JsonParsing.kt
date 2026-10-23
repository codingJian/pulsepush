package com.shing.pulsepush.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tolerant parsing helpers for list payloads whose exact shape may vary a
 * little between backend revisions (data as a bare array, or wrapped in
 * { items: [...] } / { messages: [...] } / { channels: [...] }).
 */
fun JsonElement?.asObject(): JsonObject? = if (this != null && isJsonObject) asJsonObject else null

fun JsonElement?.extractList(vararg wrapperKeys: String): JsonArray {
    if (this == null) return JsonArray()
    if (isJsonArray) return asJsonArray
    val obj = asObject() ?: return JsonArray()
    for (key in wrapperKeys) {
        val v = obj.get(key)
        if (v != null && v.isJsonArray) return v.asJsonArray
    }
    return JsonArray()
}

fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val v = get(key) ?: continue
        if (v.isJsonNull) continue
        if (v.isJsonPrimitive) return v.asString
    }
    return null
}

fun JsonObject.longOrNull(vararg keys: String): Long? {
    for (key in keys) {
        val v = get(key) ?: continue
        if (v.isJsonNull) continue
        if (v.isJsonPrimitive) {
            runCatching { return v.asLong }
            runCatching { return v.asString.toLong() }
        }
    }
    return null
}

fun JsonObject.boolOrFalse(vararg keys: String): Boolean {
    for (key in keys) {
        val v = get(key) ?: continue
        if (v.isJsonNull) continue
        if (v.isJsonPrimitive) {
            runCatching { return v.asBoolean }
            runCatching { return v.asString == "true" || v.asString == "1" }
        }
    }
    return false
}

/** Parses ISO-8601 strings / epoch seconds / epoch millis into epoch millis. */
fun parseTimeToMillis(raw: String?, fallback: Long = System.currentTimeMillis()): Long {
    if (raw.isNullOrBlank()) return fallback
    raw.toLongOrNull()?.let { n ->
        // Heuristic: 10-digit values are seconds, 13-digit are millis.
        return if (n in 1_000_000_000L..9_999_999_999L) n * 1000 else n
    }
    runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
    runCatching { return Instant.parse(raw).toEpochMilli() }
    runCatching {
        val ldt = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return fallback
}

fun JsonElement?.toChannelDto(): ChannelDto? {
    val obj = asObject() ?: return null
    val id = obj.longOrNull("id") ?: return null
    val token = obj.str("channel_token", "token") ?: return null
    return ChannelDto(
        id = id,
        channelName = obj.str("channel_name", "name") ?: "未命名通道",
        channelToken = token,
        description = obj.str("description"),
        createdAt = obj.str("created_at")
    )
}

fun JsonElement?.toMessageDto(): MessageDto? {
    val obj = asObject() ?: return null
    val msgId = obj.str("msg_id", "id") ?: return null
    val createdRaw = obj.str("created_at", "received_at", "createdAt")
    return MessageDto(
        msgId = msgId,
        channelToken = obj.str("channel_token", "channelToken") ?: "",
        title = obj.str("title") ?: "Webhook 提醒",
        body = obj.str("body", "content") ?: "",
        url = obj.str("url")?.takeIf { it.isNotBlank() },
        level = obj.str("level") ?: "info",
        isRead = obj.boolOrFalse("is_read", "read"),
        receivedAt = parseTimeToMillis(createdRaw)
    )
}
