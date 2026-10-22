package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.data.model.UnpackedOrder
import com.ocr.fastscan.databinding.FragmentReportsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsFragment : BaseFragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val unpackedAdapter = UnpackedOrdersAdapter()
    private var allUnpackedOrders = emptyList<UnpackedOrder>()
    private var activeLoadJob: Job? = null
    private var loadRequestId = 0
    private var lastTrackReport = ""
    private var lastWeightReport = ""
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedUnpackedDays = 1

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
        setupSections()
        setupUnpackedSection()
        setupTrackSection()
        setupWeightSection()
        showSection(ReportSection.UNPACKED)
    }

    private fun setupSections() {
        binding.reportTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnSectionUnpacked -> showSection(ReportSection.UNPACKED)
                R.id.btnSectionTracks -> showSection(ReportSection.TRACKS)
                R.id.btnSectionWeight -> showSection(ReportSection.WEIGHT)
            }
        }
    }

    private fun showSection(section: ReportSection) {
        binding.sectionUnpacked.visibility = if (section == ReportSection.UNPACKED) View.VISIBLE else View.GONE
        binding.sectionTracks.visibility = if (section == ReportSection.TRACKS) View.VISIBLE else View.GONE
        binding.sectionWeight.visibility = if (section == ReportSection.WEIGHT) View.VISIBLE else View.GONE
    }

    private fun setupUnpackedSection() {
        binding.rvUnpacked.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUnpacked.adapter = unpackedAdapter
        binding.etUnpackedSearch.doOnTextChanged { text, _, _, _ ->
            applyUnpackedFilter(text?.toString().orEmpty())
        }
        binding.chipUnpackedToday.setOnClickListener { selectedUnpackedDays = 1 }
        binding.chipUnpacked3Days.setOnClickListener { selectedUnpackedDays = 3 }
        binding.chipUnpackedWeek.setOnClickListener { selectedUnpackedDays = 7 }
        binding.chipUnpackedAll.setOnClickListener { selectedUnpackedDays = 0 }
        binding.btnRefreshUnpacked.setOnClickListener { loadUnpackedOrders(selectedUnpackedDays) }
        binding.btnShareUnpackedReport.setOnClickListener {
            shareReport(buildUnpackedReport(unpackedAdapter.currentList))
        }
        binding.tvUnpackedEmpty.text = getString(R.string.select_period_and_get_report)
        binding.tvUnpackedEmpty.visibility = View.VISIBLE
        binding.btnShareUnpackedReport.isEnabled = false
    }

    private fun loadUnpackedOrders(days: Int) {
        launchReportJob {
            val cutoffDate = if (days > 0) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -(days - 1))
                }.time
            } else {
                null
            }
            val shipments = cutoffDate?.let { firebaseManager.getShipmentsAfter(it) }
                ?: firebaseManager.getAllShipments()
            val packedTrackings = firebaseManager.getAllBoxEntries()
                .mapTo(mutableSetOf()) { ReportsCalculator.normalize(it.orderTracking) }

            allUnpackedOrders = shipments
                .filter {
                    ReportsCalculator.normalize(it.trackingNumber) !in packedTrackings
                }
                .map {
                    UnpackedOrder(
                        trackingNumber = it.trackingNumber,
                        clientName = it.clientName.ifBlank { "—" },
                        clientCode = it.clientCode.ifBlank { "—" },
                        grossWeight = it.weight,
                        registeredAt = it.createdAt
                    )
                }
                .sortedByDescending { it.registeredAt.toDate() }

            _binding?.tvUnpackedEmpty?.text = getString(R.string.no_unpacked_orders)
            applyUnpackedFilter(_binding?.etUnpackedSearch?.text?.toString().orEmpty())
        }
    }

    private fun applyUnpackedFilter(query: String) {
        if (_binding == null) return
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isBlank()) {
            allUnpackedOrders
        } else {
            allUnpackedOrders.filter { order ->
                order.trackingNumber.contains(normalizedQuery, ignoreCase = true) ||
                    order.clientName.contains(normalizedQuery, ignoreCase = true) ||
                    order.clientCode.contains(normalizedQuery, ignoreCase = true)
            }
        }
        unpackedAdapter.submitList(filtered)
        binding.tvUnpackedSummary.text = getString(
            R.string.unpacked_report_summary,
            filtered.size,
            filtered.sumOf { it.grossWeight }
        )
        binding.tvUnpackedEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.btnShareUnpackedReport.isEnabled = filtered.isNotEmpty()
    }

    private fun buildUnpackedReport(orders: List<UnpackedOrder>): String = buildString {
        append(getString(R.string.unpacked_orders_title)).append('\n')
        append(getString(R.string.unpacked_report_summary, orders.size, orders.sumOf { it.grossWeight }))
            .append("\n\n")
        orders.forEach { order ->
            append("• ").append(order.trackingNumber)
                .append(" — ").append(order.clientCode)
                .append(" — ")
                .append(String.format(Locale.getDefault(), "%.2f", order.grossWeight))
                .append(" кг\n")
        }
    }

    private fun setupTrackSection() {
        binding.btnTodayReport.setOnClickListener { loadTodayReport() }
        binding.btnPartyReport.setOnClickListener { selectParty() }
        binding.btnShareTrackReport.setOnClickListener { shareReport(lastTrackReport) }
    }

    private fun loadTodayReport() {
        launchReportJob {
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val clientCodeFilter = currentTrackClientCodeFilter()
            val shipments = firebaseManager.getShipmentsAfter(start.time)
                .filter { it.createdAt.toDate().before(end.time) }
                .filter {
                    clientCodeFilter.isBlank() ||
                        ReportsCalculator.normalize(it.clientCode) == clientCodeFilter
                }
            showTrackReport(buildDailyReport(start.time, shipments, clientCodeFilter))
        }
    }

    private fun selectParty() {
        launchReportJob {
            val response = apiService.getPartials(null)
            val parties = response.body().orEmpty().sortedByDescending { it.date_created }
            if (!response.isSuccessful || parties.isEmpty()) {
                showToast(R.string.no_parties_found)
                return@launchReportJob
            }
            val labels = parties.map {
                PartyNumberFormatter.display(it.partial_name).ifBlank { it.partial_code }
            }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_party_for_report)
                .setItems(labels) { _, index -> loadPartyReport(parties[index]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun loadPartyReport(party: Partial) {
        launchReportJob {
            val clientCodeFilter = currentTrackClientCodeFilter()
            val boxesResponse = apiService.getBoxesInPartial(party.guid)
            val boxes = boxesResponse.body().orEmpty()
            if (!boxesResponse.isSuccessful) {
                showToast(R.string.error_load_boxes_short)
                return@launchReportJob
            }
            val orders = boxes.chunked(4).flatMap { chunk ->
                chunk.map { box ->
                    async { apiService.getOrderDetailsBox(box.tracking_code).body().orEmpty() }
                }.awaitAll().flatten()
            }.filter {
                clientCodeFilter.isBlank() ||
                    ReportsCalculator.normalize(it.clientCode.orEmpty()) == clientCodeFilter
            }
            showTrackReport(
                buildPartyReport(
                    party = party,
                    boxWeight = boxes.sumOf { it.gross_weight },
                    orders = orders,
                    boxCount = boxes.size,
                    clientCodeFilter = clientCodeFilter
                )
            )
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
            shipments.map {
                ReportOrder(it.clientCode, it.clientName, it.trackingNumber, it.weight)
            }
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
        append(title).append('\n')
        append(getString(R.string.report_totals, orders.size, orders.sumOf { it.weight })).append('\n')
        append("────────────────\n")
        orders.groupBy { it.clientCode.ifBlank { "—" } }
            .toSortedMap()
            .forEach { (clientCode, clientOrders) ->
                val clientName = clientOrders.firstNotNullOfOrNull {
                    it.clientName.takeIf(String::isNotBlank)
                }
                append(clientCode)
                if (clientName != null) append(" — ").append(clientName)
                append('\n')
                append(
                    getString(
                        R.string.report_client_summary,
                        clientOrders.size,
                        clientOrders.sumOf { it.weight }
                    )
                ).append('\n')
                clientOrders.forEach {
                    append("• ").append(it.tracking).append(" — ")
                        .append(String.format(Locale.getDefault(), "%.2f", it.weight)).append(" кг\n")
                }
                append('\n')
            }
    }

    private fun showTrackReport(report: String) {
        lastTrackReport = report
        _binding?.tvTrackReport?.text = report
        _binding?.btnShareTrackReport?.isEnabled = report.isNotBlank()
    }

    private fun currentTrackClientCodeFilter(): String =
        ReportsCalculator.normalize(_binding?.etClientCodeFilter?.text?.toString().orEmpty())

    private fun setupWeightSection() {
        updateWeightYear()
        resetWeightReport()
        binding.btnPreviousYear.setOnClickListener {
            selectedYear--
            updateWeightYear()
            resetWeightReport()
        }
        binding.btnNextYear.setOnClickListener {
            selectedYear++
            updateWeightYear()
            resetWeightReport()
        }
        binding.etWeightClientCodeFilter.doOnTextChanged { _, _, _, _ -> resetWeightReport() }
        binding.btnWeightReport.setOnClickListener { loadMonthlyWeightReport() }
        binding.btnShareWeightReport.setOnClickListener { shareReport(lastWeightReport) }
    }

    private fun updateWeightYear() {
        _binding?.tvWeightYear?.text = selectedYear.toString()
    }

    private fun resetWeightReport() {
        lastWeightReport = ""
        _binding?.tvWeightReport?.text = getString(R.string.select_year_and_get_report)
        _binding?.btnShareWeightReport?.isEnabled = false
    }

    private fun loadMonthlyWeightReport() {
        launchReportJob {
            val clientFilter = ReportsCalculator.normalize(
                _binding?.etWeightClientCodeFilter?.text?.toString().orEmpty()
            )
            val shipments = firebaseManager.getAllShipments().map {
                ReportShipmentRecord(
                    tracking = it.trackingNumber,
                    clientCode = it.clientCode,
                    weight = it.weight,
                    registeredAt = it.createdAt.toDate()
                )
            }
            val packedTrackings = firebaseManager.getAllBoxEntries()
                .mapTo(mutableSetOf()) { it.orderTracking }
            val summaries = ReportsCalculator.monthlyWeight(
                shipments = shipments,
                packedTrackings = packedTrackings,
                year = selectedYear,
                clientCodeFilter = clientFilter
            )
            lastWeightReport = buildMonthlyWeightReport(summaries, clientFilter)
            _binding?.tvWeightReport?.text = lastWeightReport
            _binding?.btnShareWeightReport?.isEnabled = summaries.isNotEmpty()
        }
    }

    private fun buildMonthlyWeightReport(
        summaries: List<MonthlyWeightSummary>,
        clientFilter: String
    ): String = buildString {
        append(getString(R.string.monthly_weight_report_title, selectedYear)).append('\n')
        if (clientFilter.isNotBlank()) {
            append(getString(R.string.report_client_filter_applied, clientFilter)).append('\n')
        }
        if (summaries.isEmpty()) {
            append('\n').append(getString(R.string.no_report_data))
            return@buildString
        }
        append(
            getString(
                R.string.monthly_weight_year_total,
                summaries.sumOf { it.orderCount },
                summaries.sumOf { it.totalWeight },
                summaries.sumOf { it.packedWeight },
                summaries.sumOf { it.unpackedWeight }
            )
        ).append("\n────────────────\n")

        val monthNames = DateFormatSymbols.getInstance(Locale.getDefault()).months
        summaries.forEach { summary ->
            val monthName = monthNames[summary.month - 1].replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            append(
                getString(
                    R.string.monthly_weight_month_summary,
                    monthName,
                    summary.orderCount,
                    summary.totalWeight,
                    summary.packedWeight,
                    summary.unpackedWeight
                )
            ).append("\n\n")
        }
    }

    private fun launchReportJob(block: suspend CoroutineScope.() -> Unit) {
        val requestId = ++loadRequestId
        activeLoadJob?.cancel()
        activeLoadJob = launchOnViewLifecycle {
            setLoading(true)
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ReportsFragment", "Report loading failed", e)
                showToast(R.string.report_load_error)
            } finally {
                if (requestId == loadRequestId) setLoading(false)
            }
        }
    }

    private fun shareReport(report: String) {
        if (report.isBlank()) {
            showToast(R.string.no_data_to_send)
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }, getString(R.string.share_report)))
    }

    private fun setLoading(loading: Boolean) {
        val activeBinding = _binding ?: return
        activeBinding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        activeBinding.btnRefreshUnpacked.isEnabled = !loading
        activeBinding.btnTodayReport.isEnabled = !loading
        activeBinding.btnPartyReport.isEnabled = !loading
        activeBinding.btnWeightReport.isEnabled = !loading
        activeBinding.btnPreviousYear.isEnabled = !loading
        activeBinding.btnNextYear.isEnabled = !loading
        activeBinding.etWeightClientCodeFilter.isEnabled = !loading
        activeBinding.chipUnpackedToday.isEnabled = !loading
        activeBinding.chipUnpacked3Days.isEnabled = !loading
        activeBinding.chipUnpackedWeek.isEnabled = !loading
        activeBinding.chipUnpackedAll.isEnabled = !loading
    }

    override fun onDestroyView() {
        loadRequestId++
        activeLoadJob?.cancel()
        _binding?.rvUnpacked?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private enum class ReportSection { UNPACKED, TRACKS, WEIGHT }

    private data class ReportOrder(
        val clientCode: String,
        val clientName: String,
        val tracking: String,
        val weight: Double
    )
}
