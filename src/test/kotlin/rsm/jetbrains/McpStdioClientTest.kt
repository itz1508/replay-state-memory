package rsm.jetbrains

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.BufferedReader
import java.io.PipedReader
import java.io.PipedWriter
import java.io.StringReader
import java.io.StringWriter
import rsm.jetbrains.mcp.McpStdioClient

class McpStdioClientTest {

    @Test
    fun `allowed tool set is exactly the three rsm-agent tools`() {
        assertEquals(setOf("rsm_register", "rsm_replay", "rsm_remove"), McpStdioClient.ALLOWED_TOOLS)
    }

    @Test
    fun `initialize + tools list round-trip over paired pipes`() {
        // Client writes → server reads on this pipe:
        val clientToServer = PipedWriter()
        val serverIn       = PipedReader(clientToServer)
        // Server writes → client reads on this pipe:
        val serverToClient = PipedWriter()
        val clientIn       = PipedReader(serverToClient)

        val client = McpStdioClient(BufferedReader(clientIn), clientToServer)

        // Server thread: consumes 2 requests, replies to each
        val serverThread = Thread {
            val reader = BufferedReader(serverIn)
            repeat(2) {
                val line = reader.readLine() ?: return@Thread
                val req  = Json.parseToJsonElement(line).jsonObject
                val id   = req["id"]?.jsonPrimitive?.long ?: return@Thread
                val method = req["method"]!!.jsonPrimitive.content
                val response = when (method) {
                    "initialize" -> buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", buildJsonObject {
                            put("protocolVersion", "2024-11-05")
                            put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                        })
                    }
                    "tools/list" -> buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", buildJsonObject {
                            put("tools", buildJsonArray {
                                add(buildJsonObject { put("name", "rsm_register") })
                                add(buildJsonObject { put("name", "rsm_replay") })
                                add(buildJsonObject { put("name", "rsm_remove") })
                            })
                        })
                    }
                    else -> return@Thread
                }
                serverToClient.write(Json.encodeToString(JsonObject.serializer(), response))
                serverToClient.write("\n")
                serverToClient.flush()
                // consume any notifications following initialize (initialized notice)
                if (method == "initialize") { reader.readLine() /* notifications/initialized */ }
            }
            serverToClient.close()
        }
        serverThread.isDaemon = true
        serverThread.start()

        val initResp = client.initialize()
        assertTrue(initResp["result"] != null)

        val tools = client.listTools()
        assertEquals(listOf("rsm_register", "rsm_replay", "rsm_remove"), tools)

        serverThread.join(2_000)
    }
}
