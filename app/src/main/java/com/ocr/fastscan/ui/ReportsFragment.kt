package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.databinding.FragmentReportsBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsFragment : BaseFragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private var lastReport: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnTodayReport.setOnClickListener { loadTodayReport() }
        binding.btnPartyReport.setOnClickListener { selectParty() }
        binding.btnShareReport.setOnClickListener { shareReport() }
    }

    private fun loadTodayReport() {
        launchOnViewLifecycle {
            setLoading(true)
            try {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val clientCodeFilter = currentClientCodeFilter()
                val shipments = firebaseManager.getShipmentsAfter(start.time)
                    .filter { it.createdAt.toDate().before(end.time) }
                    .filter {
                        clientCodeFilter.isBlank() ||
                            it.clientCode.trim().uppercase(Locale.getDefault()) == clientCodeFilter
                    }
                showReport(buildDailyReport(start.time, shipments, clientCodeFilter))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun selectParty() {
        launchOnViewLifecycle {
            setLoading(true)
            try {
                val response = apiService.getPartials(null)
                val parties = response.body().orEmpty()
                    .sortedByDescending { it.date_created }
                if (!response.isSuccessful || parties.isEmpty()) {
                    showToast(R.string.no_parties_found)
                    return@launchOnViewLifecycle
                }
                val labels = parties.map {
                    PartyNumberFormatter.display(it.partial_name).ifBlank { it.partial_code }
                }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_party_for_report)
                    .setItems(labels) { _, index -> loadPartyReport(parties[index]) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadPartyReport(party: Partial) {
        launchOnViewLifecycle {
            setLoading(true)
            try {
                val clientCodeFilter = currentClientCodeFilter()
                val boxesResponse = apiService.getBoxesInPartial(party.guid)
                val boxes = boxesResponse.body().orEmpty()
                if (!boxesResponse.isSuccessful) {
                    showToast(R.string.error_load_boxes_short)
                    return@launchOnViewLifecycle
                }
                val orders = boxes.chunked(4).flatMap { chunk ->
                    chunk.map { box ->
                        async {
                            apiService.getOrderDetailsBox(box.tracking_code).body().orEmpty()
                        }
                    }.awaitAll().flatten()
                }.filter {
                    clientCodeFilter.isBlank() ||
                        it.clientCode.orEmpty().trim().uppercase(Locale.getDefault()) == clientCodeFilter
                }
                showReport(
                    buildPartyReport(
                        party,
                        boxes.sumOf { it.gross_weight },
                        orders,
                        boxes.size,
                        clientCodeFilter
                    )
                )
            } catch (_: Exception) {
                showToast(R.string.report_load_error)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun buildDailyReport(
        date: Date,
        shipments: List<FirestoreShipment>,
        clientCodeFilter: String
    ): String {
        var title = getString(
            R.string.daily_report_title,
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
        )
        if (clientCodeFilter.isNotBlank()) {
            title += "\n" + getString(R.string.report_client_filter_applied, clientCodeFilter)
        }
        return buildClientReport(
            title,
            shipments.map { ReportOrder(it.clientCode, it.clientName, it.trackingNumber, it.weight) }
        )
    }

    private fun buildPartyReport(
        party: Partial,
        boxWeight: Double,
        orders: List<OrderDetails>,
        boxCount: Int,
        clientCodeFilter: String
    ): String {
        val partyNumber = PartyNumberFormatter.display(party.partial_name).ifBlank { party.partial_code }
        var header = getString(R.string.party_report_title, partyNumber) + "\n" +
            getString(R.string.party_report_box_summary, boxCount, boxWeight)
        if (clientCodeFilter.isNotBlank()) {
            header += "\n" + getString(R.string.report_client_filter_applied, clientCodeFilter)
        }
        return buildClientReport(
            header,
            orders.map {
                ReportOrder(
                    it.clientCode.orEmpty(),
                    it.clientName.orEmpty(),
                    it.trackingNumber.orEmpty(),
                    it.grossWeight ?: 0.0
                )
            }
        )
    }

    private fun buildClientReport(title: String, orders: List<ReportOrder>): String = buildString {
        append(title).append("\n")
        append(getString(R.string.report_totals, orders.size, orders.sumOf { it.weight })).append("\n")
        append("────────────────\n")
        orders.groupBy { it.clientCode.ifBlank { "—" } }
            .toSortedMap()
            .forEach { (clientCode, clientOrders) ->
                val clientName = clientOrders.firstNotNullOfOrNull { it.clientName.takeIf(String::isNotBlank) }
                append(clientCode)
                if (clientName != null) append(" — ").append(clientName)
                append("\n")
                append(getString(R.string.report_client_summary, clientOrders.size, clientOrders.sumOf { it.weight }))
                    .append("\n")
                clientOrders.forEach { append("• ").append(it.tracking).append(" — ")
                    .append(String.format(Locale.getDefault(), "%.2f", it.weight)).append(" кг\n") }
                append("\n")
            }
    }

    private fun showReport(report: String) {
        lastReport = report
        _binding?.tvReport?.text = report
        _binding?.btnShareReport?.visibility = View.VISIBLE
    }

    private fun currentClientCodeFilter(): String {
        return _binding?.etClientCodeFilter?.text
            ?.toString()
            .orEmpty()
            .trim()
            .uppercase(Locale.getDefault())
    }

    private fun shareReport() {
        if (lastReport.isBlank()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastReport)
        }, getString(R.string.share_report)))
    }

    private fun setLoading(loading: Boolean) {
        _binding?.progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        _binding?.btnTodayReport?.isEnabled = !loading
        _binding?.btnPartyReport?.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ReportOrder(
        val clientCode: String,
        val clientName: String,
        val tracking: String,
        val weight: Double
    )
}
