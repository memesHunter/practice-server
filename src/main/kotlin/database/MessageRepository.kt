package com.example.messaging.database

import com.example.messaging.model.Message
import com.example.messaging.model.User
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.or
import org.litote.kmongo.and

class MessageRepository(db: Database) {
    private val collection = db.getCollection("messages", Message::class.java)

    fun addMessage(message: Message) {
        collection.insertOne(message)
    }

    fun getMessageById(id: ObjectId): Message? {
        return collection.find(Message::id eq id).firstOrNull()
    }

    fun getMessagesBySenderId(senderId: ObjectId): List<Message> {
        return collection.find(Message::senderId eq senderId).toList()
    }

    fun getMessagesByRecipientId(recipientId: ObjectId): List<Message> {
        return collection.find(Message::recipientId eq recipientId).toList()
    }

    fun getMessagesBetweenUsers(userId1: ObjectId, userId2: ObjectId): List<Message> {
        return collection.find(or(
            and(Message::senderId eq userId1, Message::recipientId eq userId2),
            and(Message::senderId eq userId2, Message::recipientId eq userId1)
        )).toList()
    }

}
