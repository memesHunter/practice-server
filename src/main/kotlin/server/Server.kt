package com.example.messaging.server

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.handlers.ConnectionHandlerTCP
import com.example.messaging.handlers.DatagramHandler
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom

class Server(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val tcpPort: Int,
    private val udpPort: Int
) {
    private val logger = LoggerFactory.getLogger(Server::class.java)
    private val serverSocket = ServerSocket(tcpPort)
    private val udpSocket = DatagramSocket(udpPort)

    fun start() {


        val connectionHandler = ConnectionHandlerTCP(serverSocket, userRepository, messageRepository, fileRepository)
        val datagramHandler = DatagramHandler(udpSocket, userRepository, messageRepository, fileRepository)

        val connectionThread = Thread { connectionHandler.run() }
        val datagramThread = Thread { datagramHandler.run() }

        connectionThread.start()
        datagramThread.start()

        logger.info("Server started on TCP port $tcpPort and UDP port $udpPort")
    }

    fun stop() {
        serverSocket.close()
        udpSocket.close()
    }
}

fun splitFile(bytes: ByteArray): List<ByteArray> {
    val chunkSize = 1024
    val totalChunks = (bytes.size + chunkSize - 1) / chunkSize

    val fileChunks = mutableListOf<ByteArray>()

    var offset = 0
    for (i in 1..totalChunks) {
        val chunk = bytes.copyOfRange(offset, offset + chunkSize)
        fileChunks.add(chunk)
        offset += chunkSize
    }

    return fileChunks
}

fun hashToken(): String {
    // Use a cryptographic hash function to generate a random token
    val randomBytes = ByteArray(16)
    SecureRandom().nextBytes(randomBytes)
    return MessageDigest.getInstance("SHA-256").digest(randomBytes).joinToString("") { "%02x".format(it) }
}