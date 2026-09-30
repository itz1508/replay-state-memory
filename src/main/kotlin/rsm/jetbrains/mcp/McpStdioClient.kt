package rsm.jetbrains.mcp

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Writer
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal MCP JSON-RPC 2.0 client hard-wired to stdio.
 *
 * Invariants:
 *   - Transport: stdio only. No HTTP, no SSE, no WebSocket, no custom transport.
 *   - Tool surface: exactly the three tools rsm-agent already exposes:
 *       rsm_register, rsm_replay, rsm_remove.
 *   - No LLM SDK. No dynamic tool discovery beyond one validating tools/list.
 */
class McpStdioClient(
    private val stdin: BufferedReader,
    private val stdout: Writer,
) {
    companion object {
        val ALLOWED_TOOLS: Set<String> = setOf("rsm_register", "rsm_replay", "rsm_remove")
        const val PROTOCOL_VERSION: String = "2024-11-05"

        fun forProcess(process: Process): McpStdioClient =
            McpStdioClient(
                stdin  = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)),
                stdout = OutputStreamWriter(process.outputStream, Charsets.UTF_8),
            )
    }

    private val nextId = AtomicLong(1L)
    private val json   = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun initialize(): JsonObject {
        val response = call("initialize", buildJsonObject {
            put("protocolVersion", PROTOCOL_VERSION)
            put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
            put("clientInfo", buildJsonObject {
                put("name", "rsm-jetbrains")
                put("version", "0.1.0")
            })
        })
        // MCP requires an "initialized" notification after the response
        notify("notifications/initialized", buildJsonObject {})
        return response
    }

    fun listTools(): List<String> {
        val response = call("tools/list", buildJsonObject {})
        val result = response["result"]?.jsonObject ?: return emptyList()
        val tools  = result["tools"]?.jsonArray  ?: return emptyList()
        val names  = tools.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        val unknown = names.toSet() - ALLOWED_TOOLS
        require(unknown.isEmpty()) { "rsm-agent exposed unknown tools: $unknown" }
        return names
    }

    fun callTool(name: String, arguments: JsonObject): JsonObject {
        require(name in ALLOWED_TOOLS) { "tool not allowed: $name" }
        return call("tools/call", buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        })
    }

    // ---------- transport ----------

    private fun call(method: String, params: JsonObject): JsonObject {
        val id = nextId.getAndIncrement()
        val req = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        writeMessage(req)
        while (true) {
            val msg = readMessage() ?: error("stream closed before response to id=$id")
            val rid = msg["id"]?.jsonPrimitive?.longOrNull
            if (rid == id) return msg
        }
    }

    private fun notify(method: String, params: JsonObject) {
        writeMessage(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        })
    }

    private fun writeMessage(obj: JsonObject) {
        val payload = json.encodeToString(JsonObject.serializer(), obj)
        stdout.write(payload)
        stdout.write("\n")
        stdout.flush()
    }

    private fun readMessage(): JsonObject? {
        val line = stdin.readLine() ?: return null
        if (line.isBlank()) return readMessage()
        return json.parseToJsonElement(line).jsonObject
    }
}
