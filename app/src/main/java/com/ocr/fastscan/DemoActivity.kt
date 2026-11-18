package com.ocr.fastscan

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.ocr.fastscan.databinding.ActivityDemoBinding
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.ThemeHelper

/**
 * Offline, read-only walkthrough for store reviewers and prospective users.
 * It deliberately does not initialize warehouse, license, or Firebase operations.
 */
class DemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoBinding
    private var selectedSection = DemoSection.SCAN

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)
        val language = prefs.getString("language", LanguageHelper.SYSTEM_LANGUAGE)
            ?: LanguageHelper.SYSTEM_LANGUAGE
        val theme = prefs.getString("theme", "system") ?: "system"

        ThemeHelper.applyTheme(theme)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.demoToolbar.setNavigationOnClickListener { finish() }
        binding.demoBottomNav.setOnItemSelectedListener { item ->
            selectedSection = when (item.itemId) {
                R.id.nav_boxes -> DemoSection.BOXES
                R.id.nav_batches -> DemoSection.BATCHES
                R.id.nav_reports -> DemoSection.REPORTS
                else -> DemoSection.SCAN
            }
            renderSection()
            true
        }
        binding.demoAction.setOnClickListener { showActionResult() }

        if (savedInstanceState == null) {
            binding.demoBottomNav.selectedItemId = R.id.nav_scan
        } else {
            selectedSection = DemoSection.fromName(
                savedInstanceState.getString(STATE_SECTION)
            )
            binding.demoBottomNav.selectedItemId = selectedSection.menuItemId
        }
        renderSection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SECTION, selectedSection.name)
        super.onSaveInstanceState(outState)
    }

    private fun renderSection() {
        val content = when (selectedSection) {
            DemoSection.SCAN -> DemoContent(
                R.string.demo_scan_title,
                R.string.demo_scan_description,
                R.string.demo_scan_primary_title,
                R.string.demo_scan_primary_detail,
                R.string.demo_scan_secondary_title,
                R.string.demo_scan_secondary_detail,
                R.string.demo_scan_action
            )
            DemoSection.BOXES -> DemoContent(
                R.string.demo_boxes_title,
                R.string.demo_boxes_description,
                R.string.demo_boxes_primary_title,
                R.string.demo_boxes_primary_detail,
                R.string.demo_boxes_secondary_title,
                R.string.demo_boxes_secondary_detail,
                R.string.demo_boxes_action
            )
            DemoSection.BATCHES -> DemoContent(
                R.string.demo_batches_title,
                R.string.demo_batches_description,
                R.string.demo_batches_primary_title,
                R.string.demo_batches_primary_detail,
                R.string.demo_batches_secondary_title,
                R.string.demo_batches_secondary_detail,
                R.string.demo_batches_action
            )
            DemoSection.REPORTS -> DemoContent(
                R.string.demo_reports_title,
                R.string.demo_reports_description,
                R.string.demo_reports_primary_title,
                R.string.demo_reports_primary_detail,
                R.string.demo_reports_secondary_title,
                R.string.demo_reports_secondary_detail,
                R.string.demo_reports_action
            )
        }

        binding.demoTitle.setText(content.title)
        binding.demoDescription.setText(content.description)
        binding.demoPrimaryTitle.setText(content.primaryTitle)
        binding.demoPrimaryDetail.setText(content.primaryDetail)
        binding.demoSecondaryTitle.setText(content.secondaryTitle)
        binding.demoSecondaryDetail.setText(content.secondaryDetail)
        binding.demoAction.setText(content.action)
        binding.demoStatus.isVisible = false
    }

    private fun showActionResult() {
        val message = when (selectedSection) {
            DemoSection.SCAN -> R.string.demo_scan_result
            DemoSection.BOXES -> R.string.demo_boxes_result
            DemoSection.BATCHES -> R.string.demo_batches_result
            DemoSection.REPORTS -> R.string.demo_reports_result
        }
        binding.demoStatus.setText(message)
        binding.demoStatus.isVisible = true
    }

    private data class DemoContent(
        val title: Int,
        val description: Int,
        val primaryTitle: Int,
        val primaryDetail: Int,
        val secondaryTitle: Int,
        val secondaryDetail: Int,
        val action: Int
    )

    private enum class DemoSection(val menuItemId: Int) {
        SCAN(R.id.nav_scan),
        BOXES(R.id.nav_boxes),
        BATCHES(R.id.nav_batches),
        REPORTS(R.id.nav_reports);

        companion object {
            fun fromName(value: String?): DemoSection =
                entries.firstOrNull { it.name == value } ?: SCAN
        }
    }

    companion object {
        private const val STATE_SECTION = "demo_section"
    }
}
