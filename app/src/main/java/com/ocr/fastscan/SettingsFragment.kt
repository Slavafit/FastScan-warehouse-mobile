package com.ocr.fastscan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.LicenseCheckResult
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.LicenseStatus
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.StatusToast
import com.ocr.fastscan.utils.ThemeHelper
import com.ocr.fastscan.utils.normalizeEmail
import com.ocr.fastscan.utils.saveVolWeightDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<ListPreference>("language")?.setOnPreferenceChangeListener { _, newValue ->
            LanguageHelper.saveLanguage(requireContext(), newValue as String)
            restartApplicationSafely()
            true
        }

        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            ThemeHelper.applyTheme(newValue as String)
            true
        }

        findPreference<Preference>("test_print")?.setOnPreferenceClickListener {
            PrintHelper(requireActivity()).printTestLabel()
            true
        }

        findPreference<Preference>("clear_token")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_token_title)
                .setMessage(R.string.clear_token_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    AppDependencies.tokenManager.clear()
                    Toast.makeText(requireContext(), R.string.token_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }

        findPreference<EditTextPreference>("vol_weight")?.setOnPreferenceChangeListener { _, newValue ->
            val newDivider = (newValue as? String)?.toDoubleOrNull() ?: 6000.0
            requireContext().saveVolWeightDivider(newDivider)
            true
        }

        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }

        setupEmailPreference()
        setupServiceDiagnostics()

        findPreference<Preference>("developer_info")?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_URL)))
            true
        }

        findPreference<Preference>("app_version")?.summary = getString(
            R.string.app_version_summary,
            getAppVersionName(),
            getAppVersionCode()
        )
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }
    }

    private fun setupEmailPreference() {
        findPreference<EditTextPreference>("user_email")?.let { emailPref ->
            emailPref.setOnBindEditTextListener { editText ->
                editText.filters = editText.filters + android.text.InputFilter { source, _, _, _, _, _ ->
                    val normalized = source.toString().lowercase(Locale.ROOT)
                    if (normalized == source.toString()) null else normalized
                }
            }

            val currentEmail = emailPref.text.orEmpty().normalizeEmail()
            if (emailPref.text != currentEmail) {
                emailPref.text = currentEmail
            }
            updateEmailSummary(emailPref, currentEmail)

            emailPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val newEmail = newValue.toString().normalizeEmail()
                emailPref.text = newEmail
                updateEmailSummary(emailPref, newEmail)
                false
            }
        }
    }

    private fun updateEmailSummary(emailPref: EditTextPreference, email: String) {
        emailPref.summary = email.ifEmpty { getString(R.string.licence_email_summary) }
    }

    private fun setupServiceDiagnostics() {
        findPreference<Preference>("service_diagnostics")?.setOnPreferenceClickListener { preference ->
            runServiceDiagnostics(preference)
            true
        }
    }

    private fun runServiceDiagnostics(preference: Preference) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val email = prefs.getString("user_email", "").orEmpty().normalizeEmail()
        if (email.isBlank()) {
            StatusToast.warning(requireContext(), R.string.license_email_missing)
            return
        }

        preference.isEnabled = false
        preference.summary = getString(R.string.diagnostics_checking)
        lifecycleScope.launch {
            val license = LicenseChecker(email).checkLicense(requireContext())
            val firebaseAuthenticated = license.firebaseAuthenticated && FirebaseAuth.getInstance().currentUser != null
            val appCheck = runCatching {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token.isNotBlank()
            }
            val firestore = runCatching {
                AppDependencies.firebaseManager.verifyWarehouseAccess()
            }

            if (!isAdded) return@launch
            val warehouse = license.warehouseId.orEmpty()
            val selectedCountry = prefs.getString(
                LicenseChecker.PREF_COUNTRY_CODE,
                LicenseChecker.DEFAULT_COUNTRY_CODE
            ).orEmpty()
            val country = license.countryCode.orEmpty().ifBlank {
                selectedCountry.ifBlank { getString(R.string.diagnostics_not_assigned) }
            }
            val serverAvailable = license.status != LicenseStatus.CONNECTION_ERROR
            val firestoreError = firestore.exceptionOrNull()
            val appCheckError = appCheck.exceptionOrNull()
            val lines = listOf(
                getString(R.string.diagnostics_license_server) to diagnosticValue(serverAvailable, license.errorMessage),
                getString(R.string.diagnostics_license) to license.toStatusText(),
                getString(R.string.diagnostics_firebase_auth) to diagnosticValue(
                    firebaseAuthenticated,
                    listOfNotNull(license.firebaseErrorCode, license.firebaseErrorMessage).joinToString(": ")
                ),
                getString(R.string.diagnostics_warehouse) to warehouse.ifBlank { getString(R.string.diagnostics_not_assigned) },
                getString(R.string.diagnostics_country) to country,
                getString(R.string.diagnostics_app_check) to diagnosticValue(
                    appCheck.getOrDefault(false),
                    appCheckError?.diagnosticDescription()
                ),
                getString(R.string.diagnostics_firestore) to diagnosticValue(
                    firestore.isSuccess,
                    firestoreError?.diagnosticDescription()
                )
            )
            val primaryServicesAvailable = serverAvailable && license.hasAccess &&
                firebaseAuthenticated && firestore.isSuccess
            preference.summary = getString(
                if (primaryServicesAvailable) R.string.diagnostics_all_available
                else R.string.diagnostics_has_errors
            )
            preference.isEnabled = true
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.service_diagnostics_title)
                .setMessage(lines.joinToString("\n") { (label, value) -> "$label: $value" })
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun diagnosticValue(available: Boolean, error: String?): String {
        val status = getString(
            if (available) R.string.diagnostics_available else R.string.diagnostics_unavailable
        )
        val detail = error?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_DIAGNOSTIC_LENGTH)
        return if (available || detail == null) status
        else getString(R.string.diagnostics_error_detail, status, detail)
    }

    private fun Throwable.diagnosticDescription(): String {
        val code = (this as? FirebaseFirestoreException)?.code?.name
        return listOfNotNull(code, localizedMessage, javaClass.simpleName)
            .distinct()
            .joinToString(": ")
    }

    private fun updateLicenseStatus(licensePref: Preference) {
        lifecycleScope.launch {
            licensePref.summary = getLicenseStatus()
        }
    }

    private suspend fun getLicenseStatus(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val email = prefs.getString("user_email", "").orEmpty().normalizeEmail()

        if (email.isBlank()) {
            return getString(R.string.license_email_missing)
        }

        return try {
            LicenseChecker(email).checkLicense(requireContext()).toStatusText()
        } catch (e: Exception) {
            Log.e("Settings", "License status check failed", e)
            getString(R.string.license_check_error)
        }
    }

    private fun LicenseCheckResult.toStatusText(): String {
        return when (status) {
            LicenseStatus.TRIAL -> {
                val formattedDate = formatLicenseDate(expiresAt)
                getString(R.string.license_trial_status, daysLeft(expiresAt), formattedDate)
            }
            LicenseStatus.ACTIVE -> {
                if (expiresAt == 0L) {
                    getString(R.string.license_active_full_status)
                } else {
                    getString(R.string.license_active_until_status, formatLicenseDate(expiresAt), daysLeft(expiresAt))
                }
            }
            LicenseStatus.NEED_ACTIVATION -> getString(R.string.license_need_activation_detail)
            LicenseStatus.EXPIRED -> getString(R.string.license_expired_message)
            LicenseStatus.REVOKED -> getString(R.string.license_revoked_message)
            LicenseStatus.DEVICE_MISMATCH -> getString(R.string.license_device_mismatch_message)
            LicenseStatus.NOT_FOUND -> getString(R.string.license_not_found_message)
            LicenseStatus.TOKEN_MISSING -> getString(R.string.license_token_missing_message)
            LicenseStatus.CONNECTION_ERROR -> getString(R.string.connection_error_message)
            LicenseStatus.INVALID -> getString(R.string.license_invalid_message)
        }
    }

    private fun formatLicenseDate(expiresAt: Long): String {
        if (expiresAt <= 0L) return "-"
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expiresAt * 1000))
    }

    private fun daysLeft(expiresAt: Long): Int {
        if (expiresAt <= 0L) return 0
        return (((expiresAt * 1000) - Date().time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun getAppVersionName(): String {
        return requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName ?: "-"
    }

    private fun getAppVersionCode(): Long {
        val packageInfo = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun restartApplicationSafely() {
        val intent = requireActivity().intent
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        )

        requireActivity().finishAffinity()
        startActivity(intent)
    }

    companion object {
        private const val DEVELOPER_URL = "https://t.me/slavafit"
        private const val MAX_DIAGNOSTIC_LENGTH = 180
    }
}
