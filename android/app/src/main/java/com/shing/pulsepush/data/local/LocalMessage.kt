package com.shing.pulsepush.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity per PRD section 7, plus a unique index on the server msg_id
 * so the same push / history-sync can never be stored twice.
 */
@Entity(
    tableName = "local_messages",
    indices = [Index(value = ["msg_id"], unique = true)]
)
data class LocalMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "msg_id")
    val msgId: String,
    @ColumnInfo(name = "channel_token")
    val channelToken: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "url")
    val url: String?,
    @ColumnInfo(name = "level")
    val level: String,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long = System.currentTimeMillis()
)
