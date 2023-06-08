package com.example.messaging.handlers

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.model.DBFile
import com.example.messaging.model.Message
import com.example.messaging.model.User
import com.example.messaging.server.hashToken
import com.example.messaging.server.splitFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress

class DatagramHandler(
    private val socket: DatagramSocket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(DatagramHandler::class.java)

    fun run() {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            while (true) {
                socket.receive(packet)
                val receivedData = packet.data.copyOf(packet.length)
                val message = String(receivedData)
                logger.info("Received message: $message")

                val response = handleRequest(message, packet.address, packet.port)

                val responseData = response.toByteArray()
                val responsePacket = DatagramPacket(
                    responseData,
                    responseData.size,
                    packet.address,
                    packet.port
                )
                socket.send(responsePacket)
            }
        } catch (e: Exception) {
            logger.error("Error receiving or sending packet: ${e.message}")
        }
    }
    /*
    * REGISTER {username} {password}
    * SEND {username} {password} {recipientUserName} {text}
    * RECEIVE {username} {password}
    * FILE {username} {password} {recipientUserName} {text} {fileName} {chunkNumber} {chunksAmount} {fileChunk}
    */
    private fun handleRequest(request: String, inetAddress: InetAddress, port: Int): String {
        val parts = request.trim().split(" ")
        if (parts.isEmpty()) {
            return "ERROR Invalid request"
        }

        return when (parts[0]) {
            "REGISTER" -> handleRegister(parts)
            "SEND" -> handleSend(parts)
            "RECEIVE" -> handleReceive(parts, inetAddress, port)
            "FILE" -> handleFile(parts)
            else -> "Invalid command"
        }
    }

    private fun handleRegister(parts: List<String>): String {
        logger.info("Registering user")

        if (parts.size != 3) {
            return "ERROR Invalid RECEIVE command"
        }

        val username = parts[1]
        val password = parts[2]
        return if (userRepository.getUserByUsername(username) != null) {
            "ERROR User already exists"
        } else {
            val newUser = User(username = username, password = password, authToken = hashToken())
            userRepository.addUser(newUser)
            "OK"
        }
    }

    private fun handleSend(parts: List<String>): String {
        logger.info("Sending message")
        if (parts.size < 6) {
            return "ERROR Invalid SEND command"
        }

        val username = parts[1]
        val password = parts[2]
        val recipientUsername = parts[3]
        val text = parts.subList(4, parts.size).joinToString(" ")

        val userStatus = validateUser(username, password)
        if (userStatus != "OK") { return userStatus }

        val user = userRepository.getUserByUsername(username)
        val recipientUser = userRepository.getUserByUsername(recipientUsername)
        return if (recipientUser == null) {
            "ERROR Recipient not found"
        } else {
            val newMessage = Message(senderId = user!!.id, recipientId = recipientUser.id, text = text, attachedFileId = null)
            messageRepository.addMessage(newMessage)
            "OK"
        }

    }

    private fun handleReceive(parts: List<String>, inetAddress: InetAddress, port: Int): String {
        logger.info("Receiving messages")
        if (parts.size != 3) {
            return "Invalid RECEIVE command"
        }

        val username = parts[1]
        val password = parts[2]

        val userStatus = validateUser(username, password)
        if (userStatus != "OK") { return userStatus }

        val user = userRepository.getUserByUsername(parts[1])
        return if (user == null) {
            "ERROR Invalid recipient"
        } else {
            val messages = messageRepository.getMessagesByRecipientId(user.id)

            coroutineScope.launch {
                messages.forEachIndexed { idx, msg ->
                    val sender = userRepository.getUserById(msg.senderId)!!
                    sendOutput("[${idx+1}/${messages.size}] ${sender.username} ${msg.text}", inetAddress, port)

                    if (msg.attachedFileId != null) {
                        val filePath = "./files/${msg.attachedFileId}"
                        val file = File(filePath)
                        if (file.exists()) {
                            val fileChunks = splitFile(file.readBytes())

                            fileChunks.forEachIndexed { index, chunk ->
                                sendOutput("[${index+1}/${fileChunks.size}] ${sender.username} ${file.name} $chunk", inetAddress, port)
                            }
                        }
                    }
                }
            }
            "OK"
        }
    }

    private val fileChunksMap: MutableMap<String, MutableMap<Int, ByteArray>> = mutableMapOf()

    private fun handleFile(parts: List<String>): String {
        logger.info("Receiving file")
        if (parts.size != 9) {
            return "Invalid FILE command"
        }

        val username = parts[1]
        val password = parts[2]
        val recipientUserName = parts[3]
        val text = parts[4]
        val fileName = parts[5]
        val chunkNumber = parts[6].toInt()
        val chunksAmount = parts[7].toInt()
        val fileChunk = parts[8].toByteArray()

        val userStatus = validateUser(username, password)
        if (userStatus != "OK") return userStatus

        val recipientUser = userRepository.getUserByUsername(recipientUserName) ?: return "ERROR Recipient not found"
        val user = userRepository.getUserByUsername(username)

        if (chunkNumber <= 0 || chunksAmount <= 0) {
            return "ERROR Invalid chunk number or chunks amount"
        }

        if (chunkNumber > chunksAmount) {
            return "ERROR Invalid chunk number or chunks amount"
        }

        val fileChunksMap = fileChunksMap.getOrPut(fileName) { mutableMapOf() }
        fileChunksMap[chunkNumber] = fileChunk

        if (fileChunksMap.size == chunksAmount) {
            val fileBytes = mutableListOf<Byte>()
            for (i in 1..chunksAmount) {
                val chunk = fileChunksMap[i]
                if (chunk != null) {
                    fileBytes.addAll(chunk.asList())
                } else {
                    return "ERROR Missing chunk number $i"
                }
            }

            val newFile = DBFile(fileName = fileName)
            val filePath = "./files/${newFile.id}}"
            try {
                FileOutputStream(filePath).use { fos ->
                    fos.write(fileBytes.toByteArray())
                }
                fileRepository.addFile(newFile)
                val newMessage = Message(senderId = user!!.id, recipientId = recipientUser.id, text = text, attachedFileId = newFile.id)
                messageRepository.addMessage(newMessage)

                return "OK"
            } catch (e: Exception) {
                return "ERROR Failed to save file: ${e.message}"
            } finally {
                this.fileChunksMap.remove(fileName)
            }
        } else {
            return "OK File chunk received $chunkNumber"
        }

    }

    private fun validateUser(username: String, password: String): String {
        val user = userRepository.getUserByUsername(username)
        return if (user == null) {
            "ERROR User not found"
        } else if (user.password != password) {
            "ERROR Incorrect password"
        } else {
            "OK"
        }
    }

    private fun sendOutput(output: String, address: InetAddress, port: Int) {
        val responseBytes = output.toByteArray()
        val responsePacket = DatagramPacket(responseBytes, responseBytes.size, address, port)
        socket.send(responsePacket)
    }
}