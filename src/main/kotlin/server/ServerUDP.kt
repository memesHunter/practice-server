package com.example.messaging.server

import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.handlers.ConnectionHandlerUDP
import java.net.DatagramPacket
import java.net.DatagramSocket

class ServerUDP(
    private val port: Int,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) {

    fun start() {
        val socket = DatagramSocket(port)
        while (true) {
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            ConnectionHandlerUDP(socket, packet, userRepository, messageRepository).start()
        }
    }
}
