package com.example.messaging.handlers

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.ServerSocket

class ConnectionHandlerTCP(
    private val serverSocket: ServerSocket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(ConnectionHandlerTCP::class.java)
    fun run() {
        while (true) {
            try {
                // Accept incoming connections from clients
                val socket = serverSocket.accept()
                logger.info("Server socket accepted")

                // Launch a new coroutine to handle the connection
                coroutineScope.launch {
                    logger.info("Creating handler for user")
                    val handler = UserMessageHandler(socket, userRepository, messageRepository, fileRepository)
                    handler.run()
                }
            } catch (e: Exception) {
                logger.error("Error accepting serverSocket connection: ${e.message}")
                return
            }
        }
    }
}
