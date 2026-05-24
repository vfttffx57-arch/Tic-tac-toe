package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AuthResult(
    val success: Boolean,
    val uid: String? = null,
    val email: String? = null,
    val errorMessage: String? = null
)

data class FirebaseProfile(
    val email: String,
    val points: Int
)

data class RedeemRequest(
    val requestId: String,
    val uid: String,
    val email: String,
    val pointsRedeemed: Int,
    val amount: Int,
    val status: String,
    val redeemCode: String,
    val timestamp: Long
)

data class MatchRecordFirebase(
    val id: String,
    val uid: String,
    val email: String,
    val outcome: String,
    val pointsChange: Int,
    val timestamp: Long
)

object FirebaseService {
    private const val API_KEY = "AIzaSyANpT9kPU3GPGo_RrHf3vrLsaHX2Q9fmwk"
    private const val PROJECT_ID = "fun-earn-50176"
    private const val AUTH_SIGNUP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"
    private const val AUTH_SIGNIN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY"
    private const val FIRESTORE_BASE_URL = "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signUp(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val request = Request.Builder()
                .url(AUTH_SIGNUP_URL)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(responseStr)
                    AuthResult(
                        success = true,
                        uid = jsonObj.getString("localId"),
                        email = jsonObj.getString("email")
                    )
                } else {
                    val errMsg = try {
                        JSONObject(responseStr).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "Signup failed: Code ${response.code}"
                    }
                    AuthResult(success = false, errorMessage = errMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Signup error", e)
            AuthResult(success = false, errorMessage = e.localizedMessage ?: "Unknown network error")
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val request = Request.Builder()
                .url(AUTH_SIGNIN_URL)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(responseStr)
                    AuthResult(
                        success = true,
                        uid = jsonObj.getString("localId"),
                        email = jsonObj.getString("email")
                    )
                } else {
                    val errMsg = try {
                        JSONObject(responseStr).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "Login failed: Code ${response.code}"
                    }
                    AuthResult(success = false, errorMessage = errMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Signin error", e)
            AuthResult(success = false, errorMessage = e.localizedMessage ?: "Unknown network error")
        }
    }

    suspend fun getUserProfile(uid: String): FirebaseProfile? = withContext(Dispatchers.IO) {
        try {
            val url = "$FIRESTORE_BASE_URL/users/$uid?key=$API_KEY"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val jsonObj = JSONObject(responseStr)
                    val fieldsObj = jsonObj.getJSONObject("fields")
                    val emailStr = fieldsObj.getJSONObject("email").getString("stringValue")
                    val pointsVal = fieldsObj.getJSONObject("points").getString("integerValue").toInt()
                    FirebaseProfile(emailStr, pointsVal)
                } else if (response.code == 404) {
                    // Profile does not exist yet
                    null
                } else {
                    Log.e("FirebaseService", "Get profile error code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "getUserProfile error", e)
            null
        }
    }

    suspend fun saveUserProfile(uid: String, email: String, points: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$FIRESTORE_BASE_URL/users/$uid?updateMask.fieldPaths=email&updateMask.fieldPaths=points&key=$API_KEY"
            
            // Build the Firestore Document Payload
            val emailVal = JSONObject().put("stringValue", email)
            val pointsVal = JSONObject().put("integerValue", points.toString())
            val fieldsObj = JSONObject().apply {
                put("email", emailVal)
                put("points", pointsVal)
            }
            val docObj = JSONObject().put("fields", fieldsObj)

            val request = Request.Builder()
                .url(url)
                .patch(docObj.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("FirebaseService", "User profile saved successfully: $uid with $points points")
                    true
                } else {
                    Log.e("FirebaseService", "Save profile failed: code ${response.code}, body: ${response.body?.string()}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "saveUserProfile error", e)
            false
        }
    }

    suspend fun submitRedeemRequest(uid: String, email: String, pointsRedeemed: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = "req_" + System.currentTimeMillis()
            val url = "$FIRESTORE_BASE_URL/redeem_requests/$id?key=$API_KEY"
            
            val fieldsObj = JSONObject().apply {
                put("requestId", JSONObject().put("stringValue", id))
                put("uid", JSONObject().put("stringValue", uid))
                put("email", JSONObject().put("stringValue", email))
                put("pointsRedeemed", JSONObject().put("integerValue", pointsRedeemed.toString()))
                put("amount", JSONObject().put("integerValue", (pointsRedeemed / 100).toString())) // 1000 pts = 10 rupees (100 pts = 1 rupee)
                put("status", JSONObject().put("stringValue", "PENDING"))
                put("redeemCode", JSONObject().put("stringValue", ""))
                put("timestamp", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            }
            val docObj = JSONObject().put("fields", fieldsObj)

            val request = Request.Builder()
                .url(url)
                .patch(docObj.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("FirebaseService", "Redeem request submitted: $id")
                    true
                } else {
                    Log.e("FirebaseService", "Submit redeem request failed: code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "submitRedeemRequest error", e)
            false
        }
    }

    suspend fun getRedeemRequests(uid: String? = null): List<RedeemRequest> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RedeemRequest>()
        try {
            val url = "$FIRESTORE_BASE_URL/redeem_requests?pageSize=300&key=$API_KEY"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val jsonObj = JSONObject(responseStr)
                    if (jsonObj.has("documents")) {
                        val documents = jsonObj.getJSONArray("documents")
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val reqId = fields.getJSONObject("requestId").getString("stringValue")
                            val rUid = fields.getJSONObject("uid").getString("stringValue")
                            val rEmail = fields.getJSONObject("email").getString("stringValue")
                            val rPoints = fields.getJSONObject("pointsRedeemed").getString("integerValue").toInt()
                            val rAmount = fields.getJSONObject("amount").getString("integerValue").toInt()
                            val rStatus = fields.getJSONObject("status").getString("stringValue")
                            val rCode = fields.optJSONObject("redeemCode")?.optString("stringValue") ?: ""
                            val rTimeStr = fields.getJSONObject("timestamp").getString("integerValue")
                            val rTime = rTimeStr.toLongOrNull() ?: System.currentTimeMillis()

                            val item = RedeemRequest(reqId, rUid, rEmail, rPoints, rAmount, rStatus, rCode, rTime)
                            if (uid == null || uid == rUid) {
                                list.add(item)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "getRedeemRequests error", e)
        }
        // Return reverse sorted by timestamp
        list.sortByDescending { it.timestamp }
        list
    }

    suspend fun recordMatch(uid: String, email: String, outcome: String, pointsChange: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = "match_" + System.currentTimeMillis() + "_" + (1000..9999).random()
            val url = "$FIRESTORE_BASE_URL/match_history/$id?key=$API_KEY"
            
            val fieldsObj = JSONObject().apply {
                put("id", JSONObject().put("stringValue", id))
                put("uid", JSONObject().put("stringValue", uid))
                put("email", JSONObject().put("stringValue", email))
                put("outcome", JSONObject().put("stringValue", outcome))
                put("pointsChange", JSONObject().put("integerValue", pointsChange.toString()))
                put("timestamp", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            }
            val docObj = JSONObject().put("fields", fieldsObj)

            val request = Request.Builder()
                .url(url)
                .patch(docObj.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "recordMatch error", e)
            false
        }
    }

    suspend fun getMatchHistoryAll(): List<MatchRecordFirebase> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MatchRecordFirebase>()
        try {
            val url = "$FIRESTORE_BASE_URL/match_history?pageSize=500&key=$API_KEY"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val jsonObj = JSONObject(responseStr)
                    if (jsonObj.has("documents")) {
                        val documents = jsonObj.getJSONArray("documents")
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val id = fields.getJSONObject("id").getString("stringValue")
                            val rUid = fields.getJSONObject("uid").getString("stringValue")
                            val rEmail = fields.getJSONObject("email").getString("stringValue")
                            val rOutcome = fields.getJSONObject("outcome").getString("stringValue")
                            val rPointsChange = fields.getJSONObject("pointsChange").getString("integerValue").toInt()
                            val rTimeStr = fields.getJSONObject("timestamp").getString("integerValue")
                            val rTime = rTimeStr.toLongOrNull() ?: System.currentTimeMillis()

                            list.add(MatchRecordFirebase(id, rUid, rEmail, rOutcome, rPointsChange, rTime))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "getMatchHistoryAll error", e)
        }
        list.sortByDescending { it.timestamp }
        list
    }

    suspend fun updateRedeemRequest(requestId: String, redeemCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Because Firestore PATCH updates the doc, we need the existing request to make sure we preserve fields,
            // or we can simply patch the individual properties we want to update.
            // Under patch url, the updateMask selects which fields are overwritten. So we can update status & redeemCode while leaving others intact.
            val url = "$FIRESTORE_BASE_URL/redeem_requests/$requestId?updateMask.fieldPaths=status&updateMask.fieldPaths=redeemCode&key=$API_KEY"
            
            val fieldsObj = JSONObject().apply {
                put("status", JSONObject().put("stringValue", "COMPLETED"))
                put("redeemCode", JSONObject().put("stringValue", redeemCode))
            }
            val docObj = JSONObject().put("fields", fieldsObj)

            val request = Request.Builder()
                .url(url)
                .patch(docObj.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("FirebaseService", "Redeem request completed: $requestId with code: $redeemCode")
                    true
                } else {
                    Log.e("FirebaseService", "updateRedeemRequest failed with code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "updateRedeemRequest error", e)
            false
        }
    }
}
