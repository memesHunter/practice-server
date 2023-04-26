import com.example.messaging.database.Database
import com.example.messaging.database.MessageRepository
import com.example.messaging.model.Message
import com.example.messaging.model.User
import com.mongodb.client.MongoClients
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MessageRepositoryTest {

    private val testSender = User(username = "sender", password = "pass", authToken = "testAuth1")
    private val testRecipient = User(username = "recipient", password = "pass", authToken = "testAuth2")
    private val testMessage = Message(
        senderId = testSender.id,
        recipientId = testRecipient.id,
        text = "testText",
        attachedFileId = null
    )
    private val messageRepository = MessageRepository(Database())

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            val client = MongoClients.create("mongodb://localhost:27017")
        }
    }

    @Test
    fun `test message can be saved and retrieved`() {
        messageRepository.addMessage(testMessage)

        val retrievedMessages = messageRepository.getMessagesByRecipientId(testMessage.recipientId)

        assertNotNull(retrievedMessages)
        assertEquals(1, retrievedMessages.size)
        assertEquals(testMessage, retrievedMessages[0])
    }

    @Test
    fun `test findByRecipient returns empty list if recipient has no messages`() {
        val retrievedMessages = messageRepository.getMessagesByRecipientId(ObjectId.get())

        assertNotNull(retrievedMessages)
        assertTrue(retrievedMessages.isEmpty())
    }

    @Test
    fun `test findById returns null if message does not exist`() {
        val retrievedMessage = messageRepository.getMessageById(ObjectId.get())

        assertNull(retrievedMessage)
    }
}
