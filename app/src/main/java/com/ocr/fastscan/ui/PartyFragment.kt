package com.ocr.fastscan.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.AddBoxRequest
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.data.model.PartialCreateRequest
import com.ocr.fastscan.data.model.PartialUpdateRequest
import com.ocr.fastscan.databinding.DialogCreatePartyBinding
import com.ocr.fastscan.databinding.DialogSearchOrderBinding
import com.ocr.fastscan.databinding.DialogSelectPartyForBoxBinding
import com.ocr.fastscan.databinding.FragmentPartyBinding
import com.ocr.fastscan.utils.ScanUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class PartyFragment : BaseFragment() {

    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!

    private var showAll = false
    private var onClosedBoxScanned: ((String) -> Unit)? = null

    private val closedBoxScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val tracking = result.data?.getStringExtra("scan_result").orEmpty().trim()
            if (tracking.isNotBlank()) {
                soundManager.playScanBeep()
                onClosedBoxScanned?.invoke(tracking)
            }
        }

    private val partiesAdapter = PartiesAdapter(
        onClick = { partial ->
            val detailFragment = PartyDetailFragment.newInstance(
                partial.guid,
                partial.codeid,
                partial.partial_code,
                PartyNumberFormatter.display(partial.partial_name).ifBlank { partial.partial_code }
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        },
        onLongClick = { partial ->
            if (partial.status != 0) {
                showRestorePartyDialog(partial)
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvParties.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParties.adapter = partiesAdapter

        binding.fabCreateParty.setOnClickListener {
            showCreatePartyDialog()
        }

        binding.btnAddClosedBox.setOnClickListener {
            showClosedBoxTrackingDialog()
        }

        binding.btnToggleFilter.setOnClickListener {
            showAll = !showAll
            updateFilterButton()
            loadParties()
        }

        updateFilterButton()
        loadParties()
    }

    private fun showClosedBoxTrackingDialog() {
        val dialogBinding = DialogSearchOrderBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.closed_box_track_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.find, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val findButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val findBox = {
                val tracking = dialogBinding.etSearchTrack.text?.toString()?.trim().orEmpty()
                if (tracking.isNotBlank()) {
                    findClosedBox(tracking, dialog, findButton)
                }
            }

            findButton.setOnClickListener { findBox() }
            dialogBinding.btnScanSearchTrack.setOnClickListener {
                ScanUtils.launchCamera(requireContext(), "barcode", closedBoxScanLauncher)
            }
            dialogBinding.etSearchTrack.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    findBox()
                    true
                } else {
                    false
                }
            }
            onClosedBoxScanned = { tracking ->
                dialogBinding.etSearchTrack.setText(tracking)
                dialogBinding.etSearchTrack.setSelection(tracking.length)
                findBox()
            }
        }
        dialog.setOnDismissListener { onClosedBoxScanned = null }
        dialog.show()
    }

    private fun findClosedBox(tracking: String, dialog: AlertDialog, findButton: View) {
        launchOnViewLifecycle {
            findButton.isEnabled = false
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxes(tCode = tracking)
                if (!response.isSuccessful) {
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }

                val box = response.body().orEmpty().firstOrNull {
                    it.trackingCode?.trim()?.equals(tracking, ignoreCase = true) == true
                }
                if (box == null) {
                    showToast(R.string.closed_box_not_found)
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }

                val assignedParty = box.partialName?.trim().takeUnless { it.isNullOrBlank() }
                    ?: box.partialCode?.trim().takeUnless { it.isNullOrBlank() }
                    ?: box.partial?.trim().takeUnless { it.isNullOrBlank() }
                if (assignedParty != null) {
                    showToast(getString(R.string.closed_box_already_assigned, assignedParty))
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }

                if (box.status != 16) {
                    showToast(R.string.box_not_orphaned_closed)
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }

                if (!box.hasRequiredDataForParty()) {
                    showToast(R.string.closed_box_data_incomplete)
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }

                val partiesResponse = apiService.getPartials(null)
                if (!partiesResponse.isSuccessful) {
                    showToast(getString(R.string.server_error, partiesResponse.code()))
                    soundManager.playError()
                    return@launchOnViewLifecycle
                }
                val parties = partiesResponse.body().orEmpty().sortedByDescending { it.date_created }
                if (parties.isEmpty()) {
                    showToast(R.string.no_parties_found)
                    return@launchOnViewLifecycle
                }

                dialog.dismiss()
                showPartySelectionForBox(box, parties)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyFragment", "Closed box lookup failed for $tracking", e)
                showToast(R.string.net_error_short)
                soundManager.playError()
            } finally {
                findButton.isEnabled = true
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun BoxItem.hasRequiredDataForParty(): Boolean =
        !guid.isNullOrBlank() &&
            !codeid.isNullOrBlank() &&
            !trackingCode.isNullOrBlank() &&
            width != null && height != null && length != null && vWeight != null

    private fun showPartySelectionForBox(box: BoxItem, parties: List<Partial>) {
        val dialogBinding = DialogSelectPartyForBoxBinding.inflate(layoutInflater)
        var attaching = false
        lateinit var dialog: AlertDialog

        val adapter = PartiesAdapter(
            onClick = { party ->
                if (!attaching) {
                    attaching = true
                    attachClosedBoxToParty(box, party) { success ->
                        attaching = false
                        if (success && dialog.isShowing) dialog.dismiss()
                    }
                }
            },
            onLongClick = {}
        )

        dialogBinding.tvClosedBoxTracking.text = box.trackingCode.orEmpty().trim()
        dialogBinding.rvParties.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvParties.adapter = adapter

        fun filterParties(query: CharSequence?) {
            val normalized = query?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val filtered = if (normalized.isBlank()) {
                parties
            } else {
                parties.filter { party ->
                    PartyNumberFormatter.display(party.partial_name).lowercase(Locale.ROOT).contains(normalized) ||
                        party.partial_code.lowercase(Locale.ROOT).contains(normalized)
                }
            }
            adapter.submitList(filtered)
            dialogBinding.tvNoParties.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            dialogBinding.rvParties.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        }

        dialogBinding.etPartySearch.doOnTextChanged { text, _, _, _ -> filterParties(text) }
        filterParties(null)

        dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_party_for_box)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { dialogBinding.rvParties.adapter = null }
        dialog.show()
    }

    private fun attachClosedBoxToParty(
        box: BoxItem,
        party: Partial,
        onComplete: (Boolean) -> Unit
    ) {
        launchOnViewLifecycle {
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                val request = AddBoxRequest(
                    guid = box.guid.orEmpty(),
                    codeid = box.codeid.orEmpty(),
                    partial = party.guid,
                    tracking_code = box.trackingCode.orEmpty().trim(),
                    width = box.width ?: 0,
                    height = box.height ?: 0,
                    length = box.length ?: 0,
                    v_weight = box.vWeight ?: 0.0,
                    gross_weight = box.grossWeight
                )
                val response = apiService.addBoxToPartial(request)
                val success = response.isSuccessful && response.body()?.success != false
                if (success) {
                    showToast(R.string.box_added_to_party)
                    soundManager.playSuccess()
                    loadParties()
                } else {
                    showToast(R.string.closed_box_attach_error)
                    soundManager.playError()
                }
                onComplete(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyFragment", "Failed to attach closed box to ${party.guid}", e)
                showToast(R.string.closed_box_attach_error)
                soundManager.playError()
                onComplete(false)
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun updateFilterButton() {
        binding.btnToggleFilter.text = if (showAll) {
            getString(R.string.in_work)
        } else {
            getString(R.string.show_all)
        }
    }

    private fun loadParties() {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            activeBinding.tvNoParties.visibility = View.GONE

            try {
                val typeParam = if (showAll) null else 0
                val response = apiService.getPartials(typeParam)
                val currentBinding = _binding ?: return@launchOnViewLifecycle

                if (response.isSuccessful) {
                    val sortedParties = response.body()
                        .orEmpty()
                        .sortedByDescending { it.date_created }

                    currentBinding.tvNoParties.visibility = if (sortedParties.isEmpty()) View.VISIBLE else View.GONE
                    currentBinding.tvNoParties.text = if (showAll) {
                        getString(R.string.no_parties_found)
                    } else {
                        getString(R.string.no_open_parties)
                    }
                    partiesAdapter.submitList(sortedParties)
                } else {
                    currentBinding.tvNoParties.text = getString(R.string.error_load)
                    currentBinding.tvNoParties.visibility = View.VISIBLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyFragment", "Load error", e)
                _binding?.tvNoParties?.text = getString(R.string.net_error)
                _binding?.tvNoParties?.visibility = View.VISIBLE
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun showRestorePartyDialog(party: Partial) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restore_party_title)
            .setMessage(getString(R.string.restore_party_message, PartyNumberFormatter.display(party.partial_name)))
            .setPositiveButton(R.string.ok) { _, _ ->
                restoreParty(party)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreParty(party: Partial) {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val request = PartialUpdateRequest(
                    guid = party.guid,
                    partial_code = party.partial_code,
                    partial_name = PartyNumberFormatter.display(party.partial_name)
                        .ifBlank { party.partial_code },
                    status = 0
                )
                val response = apiService.updatePartial(request)
                if (response.isSuccessful) {
                    showToast(R.string.party_restored)
                    soundManager.playSuccess()
                    loadParties()
                } else {
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(R.string.net_error_short)
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun showCreatePartyDialog() {
        val dialogBinding = DialogCreatePartyBinding.inflate(layoutInflater)
        val generatedCode = "NPX" + SimpleDateFormat("MMddHH", Locale.getDefault()).format(Date()) + (100000..999999).random()

        dialogBinding.etPartialCode.setText(generatedCode)
        dialogBinding.etPartialName.requestFocus()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val inputName = dialogBinding.etPartialName.text.toString().trim()
                if (inputName.isEmpty()) {
                    showToast(getString(R.string.input_part_name))
                    return@setOnClickListener
                }

                val finalName = PartyNumberFormatter.normalize(inputName)

                button.isEnabled = false

                launchOnViewLifecycle {
                    try {
                        val request = PartialCreateRequest(generatedCode, finalName)
                        val response = apiService.createPartial(request)

                        if (response.isSuccessful) {
                            showToast(R.string.part_created)
                            soundManager.playSuccess()
                            dialog.dismiss()
                            delay(300)
                            loadParties()
                        } else {
                            button.isEnabled = true
                            showToast(getString(R.string.error_code, response.code()))
                            soundManager.playError()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        button.isEnabled = true
                        showToast(R.string.net_error_short)
                        soundManager.playError()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        onClosedBoxScanned = null
        _binding?.rvParties?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
