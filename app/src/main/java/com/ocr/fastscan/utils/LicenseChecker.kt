package com.ocr.fastscan.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class LicenseChecker(private val userId: String) {

    suspend fun isLicenseValid(context: Context): Boolean {
        return checkLicense(context).hasAccess
    }

    suspend fun checkLicense(context: Context): LicenseCheckResult {
        val email = userId.normalizeEmail()
        if (email.isEmpty()) {
            return LicenseCheckResult(LicenseStatus.NEED_ACTIVATION)
        }

        val deviceId = getDeviceId(context)
        val appId = context.packageName
        val appName = getAppName(context)
        val savedToken = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_LICENSE_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
        val countryCode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_COUNTRY_CODE, DEFAULT_COUNTRY_CODE)
            .orEmpty()
            .uppercase(Locale.ROOT)
            .takeIf { it in SUPPORTED_COUNTRY_CODES }
            ?: DEFAULT_COUNTRY_CODE
        Log.d(
            "LicenseCheck",
            "Checking license: email=${email.maskEmail()}, device=${deviceId.maskDeviceId()}, app_id=$appId, app_name=$appName"
        )

        return try {
            val response = requestLicense(email, deviceId, appId, appName, countryCode, savedToken)
            saveLicenseToken(context, response.token)
            saveWarehouseId(context, response.warehouseId ?: response.legacyTenantId)
            saveCountryCode(context, response.countryCode)
            val firebaseSignIn = authenticateFirebase(response.firebaseToken)
            response.toResult(firebaseSignIn)
        } catch (e: Exception) {
            Log.e("LicenseCheck", "License server check failed", e)
            LicenseCheckResult(LicenseStatus.CONNECTION_ERROR, errorMessage = e.localizedMessage)
        }
    }

    private suspend fun requestLicense(
        email: String,
        deviceId: String,
        appId: String,
        appName: String,
        countryCode: String,
        savedToken: String?
    ): LicenseServerResponse {
        return withContext(Dispatchers.IO) {
            if (savedToken != null) {
                executeVerify(savedToken, deviceId, appId)
            } else {
                executePost(email, deviceId, appId, appName, countryCode)
            } ?: LicenseServerResponse(ok = false, status = "connection_error")
        }
    }

    private fun executePost(
        email: String,
        deviceId: String,
        appId: String,
        appName: String,
        countryCode: String
    ): LicenseServerResponse? {
        val json = gson.toJson(
            mapOf(
                "email" to email,
                "device_id" to deviceId,
                "app_id" to appId,
                "app_name" to appName,
                "country_code" to countryCode
            )
        )
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(LICENSE_SERVER_URL)
            .post(body)
            .build()

        Log.d("LicenseCheck", "Sending POST license check")
        return executeRequest(request)
    }

    private fun executeVerify(
        token: String,
        deviceId: String,
        appId: String
    ): LicenseServerResponse? {
        val json = gson.toJson(
            mapOf(
                "token" to token,
                "device_id" to deviceId,
                "app_id" to appId
            )
        )
        val request = Request.Builder()
            .url(LICENSE_VERIFY_URL)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        Log.d("LicenseCheck", "Sending POST license verification")
        return executeRequest(request)
    }

    private fun executeRequest(request: Request): LicenseServerResponse? {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d("LicenseCheck", "${request.method} license response: HTTP ${response.code}")

            if (body.isBlank()) {
                Log.w("LicenseCheck", "${request.method} license check failed: empty response")
                return null
            }

            val parsed = runCatching {
                gson.fromJson(body, LicenseServerResponse::class.java)
            }.getOrNull()

            if (!response.isSuccessful) {
                Log.w("LicenseCheck", "${request.method} license check failed: HTTP ${response.code}")
            }
            return parsed
        }
    }

    private suspend fun authenticateFirebase(customToken: String?): FirebaseSignInResult {
        if (customToken.isNullOrBlank()) {
            FirebaseAuth.getInstance().signOut()
            return FirebaseSignInResult(
                authenticated = false,
                errorCode = "missing_custom_token",
                errorMessage = "License server did not return a Firebase token"
            )
        }
        return try {
            FirebaseAuth.getInstance().signInWithCustomToken(customToken).await()
            FirebaseSignInResult(authenticated = true)
        } catch (e: Exception) {
            Log.e("LicenseCheck", "Firebase custom-token sign-in failed", e)
            FirebaseSignInResult(
                authenticated = false,
                errorCode = (e as? FirebaseAuthException)?.errorCode ?: e.javaClass.simpleName,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun saveWarehouseId(context: Context, warehouseId: String?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (warehouseId.isNullOrBlank()) remove(FirebaseManager.PREF_WAREHOUSE_ID)
            else putString(FirebaseManager.PREF_WAREHOUSE_ID, warehouseId.trim())
        }.apply()
    }

    private fun saveCountryCode(context: Context, countryCode: String?) {
        val normalized = countryCode?.trim()?.uppercase(Locale.ROOT)
        if (normalized !in SUPPORTED_COUNTRY_CODES) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_COUNTRY_CODE, normalized)
            .apply()
    }

    private fun LicenseServerResponse.toResult(firebaseSignIn: FirebaseSignInResult): LicenseCheckResult {
        val normalizedStatus = (status ?: reason).orEmpty().lowercase()
        if (!ok) {
            return LicenseCheckResult(normalizedStatus.toLicenseStatus())
        }

        val status = normalizedStatus.toLicenseStatus()
        val expiresAt = expires_at ?: 0L
        val nowSeconds = System.currentTimeMillis() / 1000
        val isExpired = expiresAt > 0L && nowSeconds >= expiresAt

        val effectiveStatus = if (status in ACCESS_STATUSES && isExpired) LicenseStatus.EXPIRED else status
        return LicenseCheckResult(
            status = effectiveStatus,
            token = token,
            expiresAt = expiresAt,
            firebaseAuthenticated = firebaseSignIn.authenticated,
            firebaseErrorCode = firebaseSignIn.errorCode,
            firebaseErrorMessage = firebaseSignIn.errorMessage,
            warehouseId = warehouseId ?: legacyTenantId,
            countryCode = countryCode
        )
    }

    private data class FirebaseSignInResult(
        val authenticated: Boolean,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    private fun String.toLicenseStatus(): LicenseStatus {
        return when (this) {
            "trial" -> LicenseStatus.TRIAL
            "active" -> LicenseStatus.ACTIVE
            "expired" -> LicenseStatus.EXPIRED
            "trial_expired" -> LicenseStatus.EXPIRED
            "revoked" -> LicenseStatus.REVOKED
            "device_mismatch" -> LicenseStatus.DEVICE_MISMATCH
            "connection_error" -> LicenseStatus.CONNECTION_ERROR
            "not_found" -> LicenseStatus.NOT_FOUND
            "already_registered" -> LicenseStatus.TOKEN_MISSING
            "need_activation", "missing_email" -> LicenseStatus.NEED_ACTIVATION
            else -> LicenseStatus.INVALID
        }
    }

    private fun saveLicenseToken(context: Context, token: String?) {
        if (token.isNullOrBlank()) return

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("license_token", token)
            .apply()
    }

    private fun getDeviceId(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.getString(KEY_LICENSE_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != BROKEN_ANDROID_ID }
            ?: UUID.randomUUID().toString()

        prefs.edit()
            .putString(KEY_LICENSE_DEVICE_ID, androidId)
            .apply()

        return androidId
    }

    private fun getAppName(context: Context): String {
        val applicationInfo = context.applicationInfo
        return context.packageManager.getApplicationLabel(applicationInfo).toString()
    }

    private fun String.maskEmail(): String {
        val atIndex = indexOf('@')
        if (atIndex <= 1) return "***"

        val name = take(atIndex)
        val domain = drop(atIndex)
        return "${name.take(1)}***$domain"
    }

    private fun String.maskDeviceId(): String {
        if (length <= 4) return "***"
        return "***${takeLast(4)}"
    }

    private data class LicenseServerResponse(
        val ok: Boolean = false,
        val status: String? = null,
        val token: String? = null,
        val expires_at: Long? = null,
        val reason: String? = null,
        @SerializedName("firebase_token") val firebaseToken: String? = null,
        @SerializedName("warehouse_id") val warehouseId: String? = null,
        @SerializedName("country_code") val countryCode: String? = null,
        // Accepted during rollout from the earlier Worker contract.
        @SerializedName("tenant_id") val legacyTenantId: String? = null
    )

    companion object {
        private const val LICENSE_SERVER_URL = "https://license-server.slavafit.workers.dev/activate"
        private const val LICENSE_VERIFY_URL = "https://license-server.slavafit.workers.dev/verify"
        private const val KEY_LICENSE_DEVICE_ID = "license_device_id"
        private const val KEY_LICENSE_TOKEN = "license_token"
        const val PREF_COUNTRY_CODE = "warehouse_country"
        const val DEFAULT_COUNTRY_CODE = "ES"
        private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ACCESS_STATUSES = setOf(LicenseStatus.TRIAL, LicenseStatus.ACTIVE)
        private val SUPPORTED_COUNTRY_CODES = setOf("ES", "US")
        private val gson = Gson()
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

data class LicenseCheckResult(
    val status: LicenseStatus,
    val token: String? = null,
    val expiresAt: Long = 0L,
    val firebaseAuthenticated: Boolean = false,
    val firebaseErrorCode: String? = null,
    val firebaseErrorMessage: String? = null,
    val warehouseId: String? = null,
    val countryCode: String? = null,
    val errorMessage: String? = null
) {
    val hasAccess: Boolean
        get() = status == LicenseStatus.TRIAL || status == LicenseStatus.ACTIVE
}

enum class LicenseStatus {
    NEED_ACTIVATION,
    TRIAL,
    ACTIVE,
    EXPIRED,
    REVOKED,
    DEVICE_MISMATCH,
    NOT_FOUND,
    TOKEN_MISSING,
    INVALID,
    CONNECTION_ERROR
}
