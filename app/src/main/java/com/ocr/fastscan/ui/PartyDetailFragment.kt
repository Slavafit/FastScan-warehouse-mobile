package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.text.InputType
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.DialogSelectBoxBinding
import com.ocr.fastscan.databinding.FragmentPartyDetailBinding
import com.ocr.fastscan.utils.FirebaseManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PartyDetailFragment : BaseFragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private var partialGuid: String = ""
    private var partialCode: String = ""
    private var partialName: String = ""
    private var isFinishing = false
    // Keep the current box list available for the final party report.
    private var currentBoxes: List<BoxInPartial> = emptyList()

    private val displayPartyNumber: String
        get() = PartyNumberFormatter.display(partialName).ifBlank { partialCode }

    private val boxesAdapter = BoxesInPartialAdapter { box ->
        val detailFragment = BoxDetailFragment.newInstance(
            boxGuid = box.guid,
            boxCodeid = box.codeid,
            boxTracking = box.tracking_code,
            boxWeight = box.gross_weight.toString(),
            partialName = partialName
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        fun newInstance(guid: String, codeid: String, code: String, name: String): PartyDetailFragment {
            return PartyDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("partial_guid", guid)
                    putString("partial_code", code)
                    putString("partial_name", name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            partialGuid = it.getString("partial_guid", "")
            partialCode = it.getString("partial_code", "")
            partialName = it.getString("partial_name", getString(R.string.no_party_name))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.part, displayPartyNumber)
        binding.btnFinishParty.text = getString(R.string.finish_part_property, displayPartyNumber)
        
        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        binding.btnAddBox.setOnClickListener { showSelectBoxDialog() }
        binding.btnGetPartyWeight.setOnClickListener { calculatePartyWeight() }
        binding.btnFinishParty.setOnClickListener { showFinishPartyDialog() }
        binding.btnEditPartyName.setOnClickListener { showEditPartyNameDialog() }
        binding.tvTitle.setOnLongClickListener {
            showEditPartyNameDialog()
            true
        }
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        loadBoxDetails()
    }

    private fun calculatePartyWeight() {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            activeBinding.btnGetPartyWeight.isEnabled = false
            try {
                val response = apiService.getBoxesInPartial(partialGuid)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle

                if (response.isSuccessful) {
                    val boxes = response.body().orEmpty()
                    currentBoxes = boxes
                    boxesAdapter.submitList(boxes)
                    binding.tvInfo.text = getString(R.string.party_detail_info, "BMG", boxes.size)

                    val totalWeight = boxes.sumOf { it.gross_weight }
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.party_weight_title)
                        .setMessage(
                            getString(
                                R.string.party_weight_result,
                                displayPartyNumber,
                                boxes.size,
                                totalWeight
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    showToast(R.string.party_weight_error)
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyDetail", "Calculate party weight error", e)
                if (isAdded) showToast(R.string.party_weight_error)
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
                _binding?.btnGetPartyWeight?.isEnabled = true
            }
        }
    }

    private fun showEditPartyNameDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.input_part)
            setText(PartyNumberFormatter.display(partialName))
            setSelection(text.length)
        }
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(
                input,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_party_name_title)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = PartyNumberFormatter.normalize(input.text.toString().trim())
                if (newName.isBlank()) {
                    showToast(R.string.input_part_name)
                    return@setOnClickListener
                }
                if (newName == partialName) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                updatePartyName(newName, dialog)
            }
        }
        dialog.show()
    }

    private fun updatePartyName(newName: String, dialog: AlertDialog) {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            try {
                val request = PartialUpdateRequest(
                    guid = partialGuid,
                    partial_code = partialCode,
                    partial_name = newName,
                    status = 0
                )
                val response = apiService.updatePartial(request)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    partialName = newName
                    binding.tvTitle.text = getString(R.string.part, displayPartyNumber)
                    binding.btnFinishParty.text =
                        getString(R.string.finish_part_property, displayPartyNumber)
                    showToast(R.string.party_name_updated)
                    soundManager.playSuccess()
                    dialog.dismiss()
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                showToast(R.string.net_error_short)
                soundManager.playError()
                Log.e("PartyDetail", "Update party name error", e)
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun loadBoxDetails() {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxesInPartial(partialGuid)
                val currentBinding = _binding ?: return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    val boxes = response.body() ?: emptyList()
                    currentBoxes = boxes
                    boxesAdapter.submitList(boxes)
                    currentBinding.tvInfo.text = getString(R.string.party_detail_info, "BMG", boxes.size)

                    Log.d("PartyDetail", "Loaded ${boxes.size} boxes for report")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyDetail", "Load boxes error", e)
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun showSelectBoxDialog() {
        val dialogBinding = DialogSelectBoxBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val adapter = OpenBoxesAdapter(partialGuid) { box ->
            addBoxToParty(box) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }
        dialogBinding.rvOpenBoxes.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvOpenBoxes.adapter = adapter

        launchOnViewLifecycle {
            try {
                val response = apiService.getOpenBoxes()
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    adapter.submitList(response.body() ?: emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) showToast(R.string.error_load_boxes_short)
            }
        }
        dialog.show()
    }

    private fun addBoxToParty(box: BoxInPartial, onSuccess: () -> Unit) {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val request = AddBoxRequest(
                    guid = box.guid,
                    codeid = box.codeid,
                    partial = partialGuid,
                    tracking_code = box.tracking_code,
                    width = box.width,
                    height = box.height,
                    length = box.length,
                    v_weight = box.v_weight,
                    gross_weight = box.gross_weight
                )
                val response = apiService.addBoxToPartial(request)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                val responseBody = response.body()
                Log.d(
                    "PartyDetail",
                    "Add box response: http=${response.code()}, success=${responseBody?.success}, message=${responseBody?.message}"
                )
                if (response.isSuccessful && responseBody?.success != false) {
                    soundManager.playSuccess()
                    runCatching(onSuccess).onFailure { error ->
                        Log.e("PartyDetail", "Box was added, but local list update failed", error)
                    }
                    showToast(R.string.box_added_to_party)
                    loadBoxDetails()
                } else {
                    showToast(R.string.error_adding_box)
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyDetail", "Failed to add box ${box.codeid} to party $partialGuid", e)
                soundManager.playError()
                if (_binding != null && isAdded) {
                    showToast(R.string.error_adding_box)
                }
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun finishParty(inventoryReports: List<BoxInventoryReport>) {
        if (isFinishing) return
        isFinishing = true

        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val request = SendPartialRequest(partial = partialGuid)
                val response = apiService.sendPartial(request)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle

                if (response.isSuccessful) {
                    soundManager.playSuccess()
                    showToast(R.string.part_finished)

                    try {
                        firebaseManager.saveBoxInventoryReports(inventoryReports)
                    } catch (e: Exception) {
                        Log.e("PartyDetail", "Batch closed but inventory snapshots were not saved", e)
                        showToast(R.string.inventory_save_error)
                    }

                    sharePartyReport(inventoryReports)
                    
                    delay(800) 
                    if (_binding != null && isAdded) {
                        parentFragmentManager.popBackStack()
                    }
                } else {
                    isFinishing = false
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isFinishing = false
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun sharePartyReport(reports: List<BoxInventoryReport>) {
        val boxes = currentBoxes
        if (boxes.isEmpty()) {
            Log.w("PartyDetail", "Party report attempted with no boxes")
            return
        }

        val legacyReport = buildString {
            append("📋 ОТЧЕТ ПО ПАРТИИ: $displayPartyNumber\n")
            append("🆔 Код: $partialCode\n")
            append(
                "📅 Дата: ${
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                }\n"
            )
            append("📦 Коробок: ${boxes.size}\n")
            append("────────────────\n")

            var totalWeight = 0.0
            boxes.forEachIndexed { index, box ->
                append("${index + 1}. 📦 ${box.tracking_code}\n")
                append("   📏 Размеры: ${box.length} × ${box.width} × ${box.height} см\n")
                append("   ⚖️ Вес: ${String.format(Locale.getDefault(), "%.2f", box.gross_weight)} кг\n")
                totalWeight += box.gross_weight
            }

            append("────────────────\n")
            append("⚖️ ОБЩИЙ ВЕС: ${String.format(Locale.getDefault(), "%.2f", totalWeight)} кг\n")
            append("\n✅ Партия успешно закрыта.")
        }
        val inventoryText = reports.joinToString("\n\n────────────────\n\n") { it.reportText }
        val text = if (inventoryText.isBlank()) {
            legacyReport
        } else {
            "$legacyReport\n\n\nBOX CONTENTS\n\n$inventoryText"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report)))
    }

    private fun showFinishPartyDialog() {
        if (isFinishing) return
        prepareInventoryReports()
    }

    private fun prepareInventoryReports() {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            activeBinding.btnFinishParty.isEnabled = false
            try {
                val boxesResponse = apiService.getBoxesInPartial(partialGuid)
                if (!boxesResponse.isSuccessful) {
                    showToast(R.string.error_load_boxes_short)
                    return@launchOnViewLifecycle
                }
                val boxes = boxesResponse.body().orEmpty()
                currentBoxes = boxes
                val ordersByBox = boxes.chunked(4).flatMap { chunk ->
                    chunk.map { box ->
                        async {
                            val response = apiService.getOrderDetailsBox(box.tracking_code)
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Failed to load box ${box.tracking_code}")
                            }
                            box to response.body().orEmpty()
                        }
                    }.awaitAll()
                }.toMap()
                val allTrackings = ordersByBox.values
                    .flatten()
                    .mapNotNull { it.trackingNumber?.trim()?.takeIf(String::isNotBlank) }
                val contentsByTracking = firebaseManager.getOrderContents(allTrackings)
                val missingCount = allTrackings
                    .map(FirebaseManager::normalizeTracking)
                    .distinct()
                    .count { contentsByTracking[it]?.items.isNullOrEmpty() }
                val reports = boxes.map { box ->
                    val contents = ordersByBox[box].orEmpty().mapNotNull { order ->
                        contentsByTracking[FirebaseManager.normalizeTracking(order.trackingNumber.orEmpty())]
                    }
                    BoxInventoryFormatter.createReport(
                        partyGuid = partialGuid,
                        partyName = displayPartyNumber,
                        boxTracking = box.tracking_code,
                        contents = contents
                    )
                }
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                val message = if (missingCount > 0) {
                    getString(R.string.inventory_missing_warning, missingCount)
                } else {
                    getString(R.string.inventory_ready_confirm, boxes.size)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.finish_party_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.finish_part) { _, _ -> finishParty(reports) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyDetail", "Failed to prepare box inventories", e)
                showToast(R.string.report_load_error)
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
                _binding?.btnFinishParty?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        _binding?.rvBoxes?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
