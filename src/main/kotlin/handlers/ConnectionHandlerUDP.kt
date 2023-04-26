package com.example.messaging.handlers

import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.model.User
import java.net.DatagramPacket
import java.net.DatagramSocket

class ConnectionHandlerUDP(
    private val socket: DatagramSocket,
    private val packet: DatagramPacket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) : Thread() {
    private lateinit var user: User

    override fun run() {
        try {
            // Read incoming messages from the client
            val input = String(packet.data, packet.offset, packet.length)
            handleMessage(input)
        } catch (e: Exception) {
            println("Error handling user message: ${e.message}")
        }
    }

    private fun handleMessage(message: String) {
        when {
            message.startsWith("REGISTER") -> {
                val parts = message.split(" ")
                if (parts.size != 3) {
                    sendResponse("ERROR Invalid syntax")
                } else {
                    val username = parts[1]
                    val password = parts[2]
                    registerUser(username, password)
                }
            }
            // handle other message types
            else -> {
                sendResponse("ERROR Unknown command")
            }
        }
    }

    private fun registerUser(username: String, password: String) {
        val existingUser = userRepository.getUserByUsername(username)
        if (existingUser != null) {
            sendResponse("ERROR User already exists")
        } else {
            val newUser = User(username = username, password = password)
            userRepository.addUser(newUser)
            sendResponse("OK")
        }
    }

    private fun sendResponse(message: String) {
        val responseBytes = message.toByteArray()
        val responsePacket = DatagramPacket(responseBytes, responseBytes.size, packet.address, packet.port)
        socket.send(responsePacket)
    }
}
