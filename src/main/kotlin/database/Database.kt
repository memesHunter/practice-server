package com.example.messaging.database

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.litote.kmongo.KMongo

class Database {
    private val mongoClient = KMongo.createClient(
        MongoClientSettings.builder()
            .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
            .build()
    )
    private val database = mongoClient.getDatabase("messaging_app")

    fun <T> getCollection(name: String, type: Class<T>) = database.getCollection(name, type)
}
