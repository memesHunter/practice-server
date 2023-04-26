package com.example.messaging.database

import com.example.messaging.model.User
import org.bson.types.ObjectId
import org.litote.kmongo.eq

class UserRepository(db: Database) {
    private val collection = db.getCollection("users", User::class.java)

    fun addUser(user: User) {
        collection.insertOne(user)
    }

    fun getUserById(id: ObjectId): User? {
        return collection.find(User::id eq id).firstOrNull()
    }

    fun getUserByUsername(username: String): User? {
        return collection.find(User::username eq username).firstOrNull()
    }

    fun getUserByAuthToken(token: String): User? {
        return collection.find(User::authToken eq token).firstOrNull()
    }
}
