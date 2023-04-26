package com.example.messaging.model

import org.bson.types.ObjectId

data class User(
    val id: ObjectId = ObjectId.get(),
    val username: String,
    val password: String,
    val authToken: String
)