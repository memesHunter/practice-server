package com.example.messaging.database

import com.example.messaging.model.DBFile
import org.bson.types.ObjectId
import org.litote.kmongo.eq

class FileRepository(db: Database) {
    private val collection = db.getCollection("files", DBFile::class.java)

    fun addFile(file: DBFile) {
        collection.insertOne(file)
    }

    fun getFileById(id: ObjectId): DBFile? {
        return collection.find(DBFile::id eq id).firstOrNull()
    }
}
