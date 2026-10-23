package com.shing.pulsepush.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM local_messages ORDER BY received_at DESC")
    fun observeAll(): Flow<List<LocalMessage>>

    @Query("SELECT * FROM local_messages WHERE msg_id = :msgId LIMIT 1")
    fun observeByMsgId(msgId: String): Flow<LocalMessage?>

    @Query("SELECT * FROM local_messages WHERE msg_id = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): LocalMessage?

    @Query("SELECT COUNT(*) FROM local_messages WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    /** IGNORE: unique msg_id index drops duplicates from push + history sync. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: LocalMessage): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(messages: List<LocalMessage>): List<Long>

    @Query("UPDATE local_messages SET is_read = 1 WHERE msg_id = :msgId")
    suspend fun markRead(msgId: String)

    @Query("DELETE FROM local_messages")
    suspend fun clearAll()
}
