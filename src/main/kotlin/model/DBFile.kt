package com.example.messaging.model

import org.bson.types.ObjectId

data class DBFile(
    val id: ObjectId = ObjectId.get(),
    val fileName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DBFile

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}