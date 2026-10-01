package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Struct for Parsed Task from Long Audio/Text
data class AIParsedTask(
    val title: String,
    val assignedToUserId: Int?,
    val dueDateStr: String?, // yyyy-mm-dd
    val points: Int,
    val isChore: Boolean
)

// Struct for Shopping Assistant Response
data class AIShoppingResponse(
    val speechMessage: String,
    val itemNamesToCheckOff: List<String>,
    val itemsToHighlight: List<String>
)

object FirebaseAIService {
    private const val TAG = "FirebaseAIService"
    private const val MODEL_NAME = "gemini-3.5-flash"

    // OkHttp Client configured with 60-second timeouts for reliable AI requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Tries to use class-safe Firebase Vertex AI SDK, and gracefully falls back to OkHttp REST if unavailable/unconfigured.
     */
    private suspend fun generateWithGemini(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        // Option A: Try Vertex AI for Firebase (Production-grade, secure, natively checks App Check)
        try {
            Log.d(TAG, "Attempting Generation using Firebase Vertex AI SDK...")
            // The Firebase Vertex AI SDK natively integrates App Check if enabled globally.
            val model = Firebase.vertexAI.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                },
                systemInstruction = systemInstruction?.let { instr ->
                    com.google.firebase.vertexai.type.content { text(instr) }
                }
            )
            val response = model.generateContent(prompt)
            val responseText = response.text
            if (!responseText.isNullOrBlank()) {
                Log.d(TAG, "Success via Firebase Vertex AI SDK")
                return@withContext responseText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Vertex AI SDK not initialized or failed. Falling back to secure Direct REST. Error: ${e.localizedMessage}")
        }

        // Option B: Fallback to Direct REST API (with keys from BuildConfig injected via secure environment variables)
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                Log.e(TAG, "No valid GEMINI_API_KEY available in secrets/BuildConfig to compile fallback call.")
                return@withContext "{}"
            }

            Log.d(TAG, "Performing Direct REST Call...")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                }
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)

                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("REST call returned code ${response.code}")
                }
                val rawBody = response.body?.string() ?: "{}"
                val jsonResponse = JSONObject(rawBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini REST Fallback generation failed entirely", e)
            return@withContext "{}"
        }
    }

    /**
     * Interacts with user about the shopping list context.
     */
    suspend fun speakToShoppingAssistant(
        userQuery: String,
        uncheckedItems: List<ShoppingItem>,
        currentLang: String
    ): AIShoppingResponse {
        val isHe = currentLang == "HE"
        val itemsListText = uncheckedItems.joinToString("\n") { item ->
            "- ID: ${item.id}, Name: ${item.name}, Category: ${item.categoryRemoteId ?: "General"}"
        }

        val systemPrompt = """
            You are "ShoppingAI", a smart family in-store voice assistant.
            You must reply ONLY in valid JSON.
            JSON structure:
            {
               "speechResponse": "Sentence in ${if (isHe) "Hebrew" else "English"} to read back to the user",
               "itemsToRead": ["Item Name 1", "Item Name 2"],
               "itemsToCheckOff": ["Item Name to check off 1"]
            }
            
            Current state of shopping list (unchecked items only):
            $itemsListText

            Instructions:
            1. Look at the user's current query or zone. 
               - E.g. if the user says "אני באזור הפירות והירקות" or "I am in the fruits zone", speak only of items listed whose category matches fruits/vegetables (or name sounds like a fruit/vegetable).
               - If the user says "תקריא לי הכל" or "read everything", list all items nicely.
               - If the user asks to tick off or check off some item, find its name in the shopping list, and add it to "itemsToCheckOff".
            2. Keep "speechResponse" warm, friendly, short, and natural for speech synthesis (TTS) in Hebrew or English. Example: "הבנתי, סימנתי את התפוחים כהושלמו. נשאר לך להביא בננות וקלמנטינות מאזור הירקות."
            3. Populate "itemsToRead" with items that are currently in their zone/category that they should buy next.
            4. Populate "itemsToCheckOff" with EXACT names from the provided shopping list that the user wants to mark as completed.
        """.trimIndent()

        val responseJsonText = generateWithGemini(
            prompt = "User Query: $userQuery",
            systemInstruction = systemPrompt
        )

        return try {
            val json = JSONObject(responseJsonText)
            val speechRes = json.optString("speechResponse", if (isHe) "אין בעיה, מה תרצה לעשות כעת?" else "Understood, what would you like to do next?")
            
            val toReadArray = json.optJSONArray("itemsToRead")
            val toRead = mutableListOf<String>()
            if (toReadArray != null) {
                for (i in 0 until toReadArray.length()) {
                    toRead.add(toReadArray.getString(i))
                }
            }

            val toCheckArray = json.optJSONArray("itemsToCheckOff")
            val toCheck = mutableListOf<String>()
            if (toCheckArray != null) {
                for (i in 0 until toCheckArray.length()) {
                    toCheck.add(toCheckArray.getString(i))
                }
            }

            AIShoppingResponse(
                speechMessage = speechRes,
                itemNamesToCheckOff = toCheck,
                itemsToHighlight = toRead
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing shopping response json: $responseJsonText", e)
            AIShoppingResponse(
                speechMessage = if (isHe) "סליחה, קרתה שגיאה בעיבוד ה-AI." else "Sorry, an AI processing error occurred.",
                itemNamesToCheckOff = emptyList(),
                itemsToHighlight = emptyList()
            )
        }
    }

    /**
     * Parses a long audio/text voice transcription into multiple chores/tasks for family members.
     */
    suspend fun parseLongVoiceTask(
        transcriptionText: String,
        familyMembers: List<User>,
        currentLang: String
    ): List<AIParsedTask> {
        val isHe = currentLang == "HE"
        val familyMembersText = familyMembers.joinToString("\n") { member ->
            "- ID: ${member.id}, Name: ${member.name}, Role: ${member.role}"
        }

        val systemPrompt = """
            You are "FamilyTaskAI", a helpful family assistant.
            The user recorded a long voice message detailing tasks or chores or shopping rules to add.
            You must parse this description and turn it into structured tasks or chores.
            
            You must reply ONLY in valid JSON.
            JSON structure:
            [
               {
                  "title": "Clean room",
                  "assignedToUserId": 1,
                  "dueDateStr": "YYYY-MM-DD",
                  "points": 10,
                  "isChore": true
               }
            ]

            Available family members you can assign tasks to:
            $familyMembersText

            Rules:
            1. "assignedToUserId" should match the ID of the family member mentioned (look for phonetic or semantic matches of names like "בני" / "אורן" / "שלומי" / "אמא", etc.). If no family member is mentioned, set to null.
            2. "dueDateStr" should be "YYYY-MM-DD" style if a time context is given (e.g., "today", "tomorrow", "on Sunday"). Take the calendar baseline date is current time (June 2026). If unspecified, you can set it to null.
            3. "points" is the points assigned to chores (range 5 to 50 based on task complexity - e.g. cleaning kitchen is 25, washing dishes is 10, taking trash out is 5).
            4. "isChore": Chores yield points and need parent verification. Standard or simple chores should be isChore=true.
            5. If no tasks can be extracted, return empty list []
        """.trimIndent()

        val responseJsonText = generateWithGemini(
            prompt = "Voice Transcript: $transcriptionText",
            systemInstruction = systemPrompt
        )

        val parsedTasks = mutableListOf<AIParsedTask>()
        try {
            val jsonArray = JSONArray(responseJsonText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                parsedTasks.add(
                    AIParsedTask(
                        title = obj.getString("title"),
                        assignedToUserId = if (obj.isNull("assignedToUserId")) null else obj.getInt("assignedToUserId"),
                        dueDateStr = if (obj.isNull("dueDateStr")) null else obj.getString("dueDateStr"),
                        points = obj.optInt("points", 10),
                        isChore = obj.optBoolean("isChore", true)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing voice task json: $responseJsonText", e)
        }
        return parsedTasks
    }
}
