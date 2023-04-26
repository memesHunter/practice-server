package com.example.messaging.server

import com.example.messaging.handlers.ConnectionHandlerTCP
import java.net.ServerSocket

class ServerTCP(
    private val serverSocket: ServerSocket,
    private val connectionHandler: ConnectionHandlerTCP
) {
    fun start() {
        println("Server listening on port ${serverSocket.localPort}")
        connectionHandler.run()
    }

    fun stop() {
        serverSocket.close()
    }
}
