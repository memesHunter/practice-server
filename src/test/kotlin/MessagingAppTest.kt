import com.example.messaging.database.Database
import com.example.messaging.database.FileRepository
import com.example.messaging.database.MessageRepository
import com.example.messaging.database.UserRepository
import com.example.messaging.server.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.litote.kmongo.KMongo
import java.io.*
import java.net.Socket
import kotlin.concurrent.thread

class MessagingAppTest {

    @Test
    fun test() {
        // Initialize the database
        val client = KMongo.createClient()
        val databaseName = "messaging_test"
        val database = client.getDatabase(databaseName)

        // Drop the entire database
        database.drop()

        // Recreate the database
        client.getDatabase(databaseName)

        val db = Database()
        db.reset()
        val userRepo = UserRepository(db)
        val messageRepo = MessageRepository(db)
        val fileRepo = FileRepository(db)

        // Initialize and start the server
        val server = Server(userRepo, messageRepo, fileRepo, 12345, 54321)
        thread {
            server.start()
        }


        // Connect first client
        val socket = Socket("localhost", 8080)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        // Test registration
        output.writeUTF("REGISTER alice password")
        output.flush()
        var response = input.readUTF()
        assertEquals("OK", response)

        // Test login
        output.writeUTF("LOGIN alice password")
        output.flush()
        response = input.readUTF()
        assertEquals("OK", response)

        // Connect second client
        val socket2 = Socket("localhost", 8080)
        val input2 = DataInputStream(socket2.getInputStream())
        val output2 = DataOutputStream(socket2.getOutputStream())

        // Test registration
        output2.writeUTF("REGISTER bob pass")
        output2.flush()
        response = input2.readUTF()
        assertEquals("OK", response)

        // Test invalid password
        output2.writeUTF("LOGIN bob password")
        output2.flush()
        response = input2.readUTF()
        assertEquals("ERROR Incorrect password", response)

        // Test login
        output2.writeUTF("LOGIN bob pass")
        output2.flush()
        response = input2.readUTF()
        assertEquals("OK", response)


        // Test sending messages
        output.writeUTF("SEND bob Hello, Bob!")
        output.flush()
        response = input.readUTF()
        assertEquals("OK", response)

        output2.writeUTF("SEND alice Hello, Alice!")
        output2.flush()
        response = input2.readUTF()
        assertEquals("OK", response)


        // Test logout
        output.writeUTF("LOGOUT")
        output.flush()
        response = input.readUTF()
        assertEquals("OK", response)

        output2.writeUTF("LOGOUT")
        output2.flush()
        response = input2.readUTF()
        assertEquals("OK", response)

        // Clean up
        server.stop()
        socket.close()
        socket2.close()
    }
}
