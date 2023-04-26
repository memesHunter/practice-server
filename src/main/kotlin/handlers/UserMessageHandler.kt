package com.example.messaging.handlers

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.model.DBFile
import com.example.messaging.model.Message
import com.example.messaging.model.User
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

class UserMessageHandler(
    private val socket: Socket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository
) : Runnable {
    private val logger = LoggerFactory.getLogger(UserMessageHandler::class.java)
    private var running = true
    private var authToken: String? = null
    private val user: User?
        get() {
            return authToken?.let { userRepository.getUserByAuthToken(it) }
        }

    override fun run() {
        logger.info("Handler started")
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        try {
            // Read incoming messages from the client
            while (running) {
                val message = input.readUTF()
                logger.info("Message received: $message")
                if (authToken == null) {
                    // Require authentication for all messages except REGISTER
                    if (message.startsWith("REGISTER")) {
                        handleRegister(message, output)
                    } else if (message.startsWith("LOGIN")) {
                        handleLogin(message, output)
                    } else {
                        output.writeUTF("ERROR Unauthorized")
                        output.flush()
                    }
                } else {
                    handleMessage(message, input, output)
                }
            }
        } catch (e: Exception) {
            println("Error handling user message: ${e.message}")
        } finally {
            socket.close()
        }
    }

    fun stop() {
        running = false
    }

    private fun hashToken(): String {
        // Use a cryptographic hash function to generate a random token
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        return MessageDigest.getInstance("SHA-256").digest(randomBytes).joinToString("") { "%02x".format(it) }
    }

    private fun handleRegister(message: String, output: DataOutputStream) {
        logger.info("Registering user")
        val parts = message.split(" ")
        if (parts.size != 3) {
            output.writeUTF("ERROR Invalid syntax")
        } else {
            val username = parts[1]
            val password = parts[2]
            val user = userRepository.getUserByUsername(username)
            if (user != null) {
                output.writeUTF("ERROR User already exists")
            } else {
                val hashedToken = hashToken()
                val newUser = User(username = username, password = password, authToken = hashedToken)
                userRepository.addUser(newUser)
                output.writeUTF("OK")
            }
        }
        output.flush()
    }

    private fun handleLogin(message: String, output: DataOutputStream) {
        logger.info("Logging user in")
        val parts = message.split(" ")
        if (parts.size != 3) {
            output.writeUTF("ERROR Invalid syntax")
        } else {
            val username = parts[1]
            val password = parts[2]
            val user = userRepository.getUserByUsername(username)
            if (user == null) {
                output.writeUTF("ERROR User not found")
            } else if (user.password != password) {
                output.writeUTF("ERROR Incorrect password")
            } else {
                authToken = user.authToken
                output.writeUTF("OK")
            }
        }
        output.flush()
    }

    /*
    * REGISTER {username} {password}
    * LOGIN {username} {password}
    * LOGOUT
    * SEND {recipientUserName} {text}
    * RECEIVE
    * FILE {recipientUserName} {text} {fileName} {fileLength}
    *      [4KB chunk
    *      ...
    *      4KB chunk]
    * */
    private fun handleMessage(message: String, reader: DataInputStream, writer: DataOutputStream) {
        when {
            // handle sending message
            message.startsWith("SEND") -> {
                logger.info("Sending message")
                val parts = message.split(" ", limit = 3)
                if (parts.size != 3) {
                    writer.writeUTF("ERROR Invalid syntax\n")
                    writer.flush()
                } else {
                    val recipient = parts[1]
                    val messageBody = parts[2]
                    sendMessage(recipient, messageBody, writer)
                }
            }
            // handle receiving messages
            message == "RECEIVE" -> {
                logger.info("Receiving messages")
                receiveMessages(writer)
            }
            // handle sending file
            message.startsWith("FILE") -> {
                logger.info("Sending file")
                val parts = message.split(" ", limit = 5)
                if (parts.size != 5) {
                    writer.writeUTF("ERROR Invalid syntax")
                    writer.flush()
                } else {
                    val recipient = parts[1]
                    val fileName = parts[2]
                    val fileSize = parts[3].toInt()
                    val text = parts[4]
                    sendFile(recipient, text, fileName, fileSize, reader, writer)
                }
            }
            // handle logout
            message == "LOGOUT" -> {
                logger.info("Logging user out")
                running = false
                authToken = null
                writer.writeUTF("OK")
                writer.flush()
            }
            // handle other message types
            else -> {
                logger.error("Invalid command")
                writer.writeUTF("ERROR Unknown command")
                writer.flush()
            }
        }
    }

    private fun sendMessage(recipient: String, message: String, writer: DataOutputStream) {
        val recipientUser = userRepository.getUserByUsername(recipient)
        if (recipientUser == null) {
            writer.writeUTF("ERROR Recipient not found")
            writer.flush()
        } else if (user == null) {
            writer.writeUTF("ERROR Invalid sender user")
            writer.flush()
        } else {
            val newMessage = Message(senderId = user!!.id, recipientId = recipientUser.id, text = message, attachedFileId = null)
            messageRepository.addMessage(newMessage)
            writer.writeUTF("OK")
            writer.flush()
        }
    }

    private fun receiveMessages(writer: DataOutputStream) {
        if (user == null) {
            writer.writeUTF("ERROR Invalid recipient")
            writer.flush()
        } else {
            val messages = messageRepository.getMessagesByRecipientId(user!!.id)

            writer.writeUTF("OK ${messages.size}")
            messages.forEach {
                val sender = userRepository.getUserById(it.senderId)
                // TODO null sender handling
                writer.writeUTF("${sender?.username} ${it.text}")
            }
            writer.flush()
        }
    }

    private fun sendFile(recipient: String, message: String, fileName: String, fileSize: Int, reader: DataInputStream, writer: DataOutputStream) {
        val recipientUser = userRepository.getUserByUsername(recipient)
        if (recipientUser == null) {
            writer.writeUTF("ERROR Recipient not found")
            writer.flush()
        } else if (user == null) {
            writer.writeUTF("ERROR Invalid sender user")
            writer.flush()
        } else {
            val newFile = DBFile(fileName = fileName)
            val fileOutputStream = FileOutputStream("./files/${newFile.id}")
            try {
                var size = fileSize

                val buffer = ByteArray(4 * 1024)
                while (size > 0) {
                    val bytes = reader.read(buffer, 0, minOf(buffer.size, size))
                    if (bytes == -1) break

                    fileOutputStream.write(String(buffer).toByteArray(), 0, bytes)
                    size -= bytes
                }

                fileRepository.addFile(newFile)
                val newMessage = Message(senderId = user!!.id, recipientId = recipientUser.id, text = message, attachedFileId = newFile.id)
                messageRepository.addMessage(newMessage)
                writer.writeUTF("OK")
                writer.flush()

            } catch (e: IOException) {
                writer.writeUTF("ERROR Unable to receive file")
                writer.flush()
                // TODO delete file if an error occurred
            } finally {
                fileOutputStream.close()
            }
        }
    }
    // other methods for handling messages
}
