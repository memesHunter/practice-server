package com.example.messaging.handlers

import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.model.DBFile
import com.example.messaging.model.Message
import com.example.messaging.model.User
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

class UserMessageHandler(
    private val socket: Socket,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository
) : Runnable {
    private var running = true
    private var authToken: String? = null

    override fun run() {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        try {
            // Read incoming messages from the client
            while (running) {
                val message = input.readUTF()
                if (authToken == null) {
                    // Require authentication for all messages except REGISTER
                    if (message.startsWith("REGISTER")) {
                        handleRegister(message, output)
                    } else if (message.startsWith("LOGIN")) {
                        handleLogin(message, output)
                    } else {
                        output.writeUTF("ERROR Unauthorized\n")
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
        val parts = message.split(" ")
        if (parts.size != 3) {
            output.writeUTF("ERROR Invalid syntax\n")
        } else {
            val username = parts[1]
            val password = parts[2]
            val user = userRepository.getUserByUsername(username)
            if (user != null) {
                output.writeUTF("ERROR User already exists\n")
            } else {
                val hashedToken = hashToken()
                authToken = hashedToken
                val newUser = User(username = username, password = password, authToken = hashedToken)
                userRepository.addUser(newUser)
                output.writeUTF("OK\n")
            }
        }
        output.flush()
    }

    private fun handleLogin(message: String, output: DataOutputStream) {
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
    * SEND {senderUserName} {recipientUserName} {text}
    * RECEIVE {username}
    * FILE {senderUserName} {recipientUserName} {text} {fileName} {fileLength}
    *      [4KB chunk
    *      ...
    *      4KB chunk]
    * */
    private fun handleMessage(message: String, reader: DataInputStream, writer: DataOutputStream) {
        when {
            // handle sending message
            message.startsWith("SEND") -> {
                val parts = message.split(" ", limit = 4)
                if (parts.size != 4) {
                    writer.writeUTF("ERROR Invalid syntax\n")
                    writer.flush()
                } else {
                    val sender = parts[1]
                    val recipient = parts[2]
                    val messageBody = parts[3]
                    sendMessage(sender, recipient, messageBody, writer)
                }
            }
            // handle receiving messages
            message.startsWith("RECEIVE") -> {
                val parts = message.split(" ", limit = 2)
                if (parts.size != 2) {
                    writer.writeUTF("ERROR Invalid syntax\n")
                    writer.flush()
                } else {
                    receiveMessages(parts[1], writer)
                }
            }
            // handle sending file
            message.startsWith("FILE") -> {
                val parts = message.split(" ", limit = 6)
                if (parts.size != 6) {
                    writer.writeUTF("ERROR Invalid syntax\n")
                    writer.flush()
                } else {
                    val sender = parts[1]
                    val recipient = parts[2]
                    val fileName = parts[3]
                    val fileSize = parts[4].toInt()
                    val text = parts[5]
                    sendFile(sender, recipient, text, fileName, fileSize, reader, writer)
                }
            }
            // handle logout
            message.startsWith("LOGOUT") -> {
                running = false
                authToken = null
            }
            // handle other message types
            else -> {
                writer.writeUTF("ERROR Unknown command\n")
                writer.flush()
            }
        }
    }

    private fun sendMessage(sender:String, recipient: String, message: String, writer: DataOutputStream) {
        val recipientUser = userRepository.getUserByUsername(recipient)
        val senderUser = userRepository.getUserByUsername(sender)
        if (recipientUser == null) {
            writer.writeUTF("ERROR Recipient not found\n")
            writer.flush()
        } else if (senderUser == null) {
            writer.writeUTF("ERROR Invalid sender user\n")
            writer.flush()
        } else {
            val newMessage = Message(senderId = senderUser.id, recipientId = recipientUser.id, text = message, attachedFile = null)
            messageRepository.addMessage(newMessage)
            writer.writeUTF("OK\n")
            writer.flush()
        }
    }

    private fun receiveMessages(recipient: String, writer: DataOutputStream) {
        val recipientUser = userRepository.getUserByUsername(recipient)
        if (recipientUser == null) {
            writer.writeUTF("ERROR Invalid recipient\n")
            writer.flush()
        } else {
            val messages = messageRepository.getMessagesByRecipientId(recipientUser.id)

            writer.writeUTF("OK ${messages.size}\n")
            messages.forEach {
                val sender = userRepository.getUserById(it.senderId)
                // TODO null sender handling
                writer.writeUTF("${sender?.username} ${it.text}\n")
            }
            writer.flush()
        }
    }

    private fun sendFile(sender: String, recipient: String, message: String, fileName: String, fileSize: Int, reader: DataInputStream, writer: DataOutputStream) {
        val recipientUser = userRepository.getUserByUsername(recipient)
        val senderUser = userRepository.getUserByUsername(sender)
        if (recipientUser == null) {
            writer.writeUTF("ERROR Recipient not found\n")
            writer.flush()
        } else if (senderUser == null) {
            writer.writeUTF("ERROR Invalid sender user\n")
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
                writer.writeUTF("OK\n")
                writer.flush()

            } catch (e: IOException) {
                writer.writeUTF("ERROR Unable to receive file\n")
                writer.flush()
                // TODO delete file if an error occurred
            } finally {
                fileOutputStream.close()
            }
        }
    }
    // other methods for handling messages
}
