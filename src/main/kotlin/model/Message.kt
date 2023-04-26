package com.example.messaging.model

import org.bson.types.ObjectId
import java.time.LocalDateTime

data class Message(
    val id: ObjectId = ObjectId.get(),
    val senderId: ObjectId,
    val recipientId: ObjectId,
    val text: String,
    val attachedFile: ObjectId?,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (id != other.id) return false
        if (senderId != other.senderId) return false
        if (recipientId != other.recipientId) return false
        if (text != other.text) return false
        if (attachedFile != other.attachedFile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + attachedFile.hashCode()
        return result
    }
}