package com.raybans.claudeassistant.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Claude API Client for communicating with Anthropic's Claude API
 * Optimized for voice assistant use cases with Meta Ray-Bans
 */
class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val MAX_TOKENS = 1024

        // System prompt optimized for voice assistant
        private const val SYSTEM_PROMPT = """Je bent Claude, een behulpzame AI-assistent die via Meta Ray-Bans wordt gebruikt.
Houd je antwoorden kort en bondig omdat ze worden voorgelezen.
Gebruik eenvoudige zinnen die makkelijk te volgen zijn via audio.
Als je een lange lijst hebt, beperk je tot de belangrijkste 3-5 items.
Vermijd markdown formatting, code blocks, of speciale tekens.
Spreek in het Nederlands tenzij de gebruiker een andere taal gebruikt."""
    }

    private val conversationHistory = mutableListOf<Message>()

    /**
     * Send a message to Claude and get a response
     * Maintains conversation history for context
     */
    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Add user message to history
            conversationHistory.add(Message(role = "user", content = userMessage))

            val request = ClaudeRequest(
                model = MODEL,
                maxTokens = MAX_TOKENS,
                system = SYSTEM_PROMPT,
                messages = conversationHistory.toList()
            )

            val requestBody = gson.toJson(request).toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                conversationHistory.removeLast() // Remove failed message
                return@withContext Result.failure(Exception("API Error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val claudeResponse = gson.fromJson(responseBody, ClaudeResponse::class.java)
            val assistantMessage = claudeResponse.content.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("No text content in response"))

            // Add assistant response to history
            conversationHistory.add(Message(role = "assistant", content = assistantMessage))

            // Keep conversation history manageable (last 20 messages)
            while (conversationHistory.size > 20) {
                conversationHistory.removeAt(0)
            }

            Result.success(assistantMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear conversation history to start fresh
     */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Get current conversation length
     */
    fun getConversationLength(): Int = conversationHistory.size
}

// Data classes for API communication

data class Message(
    val role: String,
    val content: String
)

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<Message>
)

data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerializedName("stop_reason") val stopReason: String?
)

data class ContentBlock(
    val type: String,
    val text: String
)
