package com.example.messaging.handlers

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import kotlinx.coroutines.*
import java.net.ServerSocket

class ConnectionHandlerTCP(
    private val serverSocket: ServerSocket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val coroutineContext: CoroutineDispatcher = Dispatchers.Default
) {
    fun run() = runBlocking {
        while (true) {
            try {
                // Accept incoming connections from clients
                val socket = withContext(Dispatchers.IO) {
                    serverSocket.accept()
                }

                // Create a new user message handler for the client
                val handler = UserMessageHandler(socket, userRepository, messageRepository, fileRepository)

                // Start the user message handler in a new coroutine
                launch(coroutineContext) {
                    handler.run()
                }
            } catch (e: Exception) {
                println("Error accepting connection: ${e.message}")
            }
        }
    }
}
