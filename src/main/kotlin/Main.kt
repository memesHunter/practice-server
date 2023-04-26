package com.example.messaging

import com.example.messaging.server.ServerTCP
import com.example.messaging.handlers.ConnectionHandlerTCP
import com.example.messaging.database.Database
import com.example.messaging.database.FileRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.database.MessageRepository
import java.net.ServerSocket

fun main() {
    // Initialize the database
    val db = Database()
    val userRepo = UserRepository(db)
    val messageRepo = MessageRepository(db)
    val fileRepo = FileRepository(db)

    // Initialize the server socket
    val socket = ServerSocket(12345)

    // Initialize the connection handler
    val connectionHandler = ConnectionHandlerTCP(
        socket,
        userRepo,
        messageRepo,
        fileRepo
    )

    // Initialize and start the server
    val server = ServerTCP(socket, connectionHandler)
    server.start()
}
