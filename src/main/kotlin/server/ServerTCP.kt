package com.example.messaging.server

import com.example.messaging.handlers.ConnectionHandlerTCP
import org.slf4j.LoggerFactory
import java.net.ServerSocket

class ServerTCP(
    private val serverSocket: ServerSocket,
    private val connectionHandler: ConnectionHandlerTCP
) {
    private val logger = LoggerFactory.getLogger(ServerTCP::class.java)
    fun start() {
        logger.info("Server listening on port ${serverSocket.localPort}")
        connectionHandler.run()
    }

    fun stop() {
        serverSocket.close()
    }
}
