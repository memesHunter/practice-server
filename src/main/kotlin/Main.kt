package com.example.messaging

import com.example.messaging.server.Server
import com.example.messaging.handlers.ConnectionHandlerTCP
import com.example.messaging.database.Database
import com.example.messaging.database.FileRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.handlers.DatagramHandler
import java.net.DatagramSocket
import java.net.ServerSocket
import kotlin.concurrent.thread

fun main() {
    // Initialize the database
    val db = Database()
    val userRepo = UserRepository(db)
    val messageRepo = MessageRepository(db)
    val fileRepo = FileRepository(db)

    // Initialize and start the server
    val server = Server(userRepo, messageRepo, fileRepo, 12345, 54321)
    server.start()
}
