package com.ocr.fastscan.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.ocr.fastscan.R
import com.ocr.fastscan.data.ProductCatalog
import com.ocr.fastscan.data.model.OrderContent
import com.ocr.fastscan.data.model.OrderContentItem
import com.ocr.fastscan.data.model.ProductCatalogItem
import com.ocr.fastscan.databinding.DialogOrderContentBinding
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class OrderContentDialogFragment : DialogFragment() {
    private var _binding: DialogOrderContentBinding? = null
    private val binding get() = _binding!!

    private val tracking: String by lazy {
        requireArguments().getString(ARG_TRACKING).orEmpty()
    }
    private val items = mutableListOf<OrderContentItem>()
    private var catalog = ProductCatalog.defaultItems
    private var selectedCategory: String? = null
    private var selectedProduct: ProductCatalogItem? = null
    private var quantity = 1
    private var reviewIndex = 0
    private var editingProductId: String? = null
    private var screen = Screen.CATEGORIES

    private val voiceQuantityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val candidates = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        val recognized = candidates.firstNotNullOfOrNull(::parseQuantity)
        if (recognized == null) {
            Toast.makeText(requireContext(), R.string.quantity_not_recognized, Toast.LENGTH_SHORT).show()
        } else {
            setQuantity(recognized)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOrderContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvContentTracking.text = getString(R.string.content_tracking, tracking)
        setupQuantityControls()
        setupNavigationControls()
        loadData()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun loadData() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val manager = AppDependencies.firebaseManager
            val remoteCatalog = manager.getProductCatalog()
            catalog = if (remoteCatalog.isEmpty()) {
                runCatching { manager.seedProductCatalog(ProductCatalog.defaultItems) }
                ProductCatalog.defaultItems
            } else {
                val remoteById = remoteCatalog.associateBy { it.id }
                (ProductCatalog.defaultItems.map { remoteById[it.id] ?: it } +
                    remoteCatalog.filter { it.id !in ProductCatalog.defaultItems.map(ProductCatalogItem::id).toSet() })
                    .sortedWith(compareBy(ProductCatalogItem::categoryId, ProductCatalogItem::sortOrder))
            }
            manager.getOrderContent(tracking)?.items?.let {
                items.clear()
                items.addAll(it.filter { entry -> entry.quantity > 0 })
            }
            setLoading(false)
            showCategories()
        }
    }

    private fun setupQuantityControls() {
        binding.btnQuantityMinus.setOnClickListener { setQuantity(quantity - 1) }
        binding.btnQuantityPlus.setOnClickListener { setQuantity(quantity + 1) }
        listOf(
            binding.btnQuick1 to 1,
            binding.btnQuick2 to 2,
            binding.btnQuick3 to 3,
            binding.btnQuick4 to 4
        ).forEach { (button, value) -> button.setOnClickListener { setQuantity(value) } }
        binding.btnManualQuantity.setOnClickListener { showManualQuantityDialog() }
        binding.btnVoiceQuantity.setOnClickListener { startVoiceQuantityInput() }
        binding.btnAddContentItem.setOnClickListener { addSelectedItem() }
    }

    private fun setupNavigationControls() {
        binding.btnContentBack.setOnClickListener {
            when (screen) {
                Screen.CATEGORIES -> dismiss()
                Screen.PRODUCTS, Screen.REVIEW -> showCategories()
                Screen.QUANTITY -> {
                    if (editingProductId != null) showReview() else showProducts(selectedCategory.orEmpty())
                }
            }
        }
        binding.btnReviewContents.setOnClickListener { showReview() }
        binding.btnContentFinish.setOnClickListener { saveAndFinish() }
        binding.btnReviewPrevious.setOnClickListener {
            if (items.isNotEmpty()) {
                reviewIndex = (reviewIndex - 1 + items.size) % items.size
                renderReviewItem()
            }
        }
        binding.btnReviewNext.setOnClickListener {
            if (items.isNotEmpty()) {
                reviewIndex = (reviewIndex + 1) % items.size
                renderReviewItem()
            }
        }
        binding.btnReviewEdit.setOnClickListener {
            val item = items.getOrNull(reviewIndex) ?: return@setOnClickListener
            editingProductId = item.productId
            val product = catalog.firstOrNull { it.id == item.productId } ?: item.toCatalogItem()
            showQuantity(product, item.quantity)
        }
        binding.btnReviewDelete.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            items.removeAt(reviewIndex)
            reviewIndex = reviewIndex.coerceAtMost((items.size - 1).coerceAtLeast(0))
            updateSummary()
            if (items.isEmpty()) showCategories() else renderReviewItem()
        }
    }

    private fun showCategories() {
        screen = Screen.CATEGORIES
        selectedCategory = null
        selectedProduct = null
        editingProductId = null
        showPanel(binding.gridContentChoices)
        binding.tvContentTitle.setText(R.string.order_content)
        val categories = listOf(
            ProductCatalog.CATEGORY_CLOTHING to R.string.category_clothing,
            ProductCatalog.CATEGORY_FOOTWEAR to R.string.category_footwear,
            ProductCatalog.CATEGORY_ELECTRONICS to R.string.category_electronics,
            ProductCatalog.CATEGORY_FOOD to R.string.category_food,
            ProductCatalog.CATEGORY_BAGS to R.string.category_bags,
            ProductCatalog.CATEGORY_OTHER to R.string.category_other
        )
        fillGrid(categories.map { (id, title) -> Choice(getString(title)) { openCategory(id) } })
        updateSummary()
    }

    private fun openCategory(categoryId: String) {
        selectedCategory = categoryId
        val products = catalog.filter { it.categoryId == categoryId }.sortedBy { it.sortOrder }
        if (products.size == 1) {
            showQuantity(products.first())
        } else {
            showProducts(categoryId)
        }
    }

    private fun showProducts(categoryId: String) {
        screen = Screen.PRODUCTS
        selectedCategory = categoryId
        showPanel(binding.gridContentChoices)
        val language = currentLanguage()
        val choices = catalog
            .filter { it.categoryId == categoryId }
            .sortedBy { it.sortOrder }
            .map { product ->
                Choice(ProductCatalog.displayName(product, language)) { showQuantity(product) }
            }
            .toMutableList()
        if (categoryId == ProductCatalog.CATEGORY_OTHER && choices.size < 6) {
            choices += Choice(getString(R.string.custom_product)) { showCustomProductDialog() }
        }
        fillGrid(choices)
    }

    private fun showQuantity(product: ProductCatalogItem, initialQuantity: Int = 1) {
        screen = Screen.QUANTITY
        selectedProduct = product
        selectedCategory = product.categoryId
        showPanel(binding.quantityPanel)
        binding.tvSelectedProduct.text = ProductCatalog.displayName(product, currentLanguage())
        setQuantity(initialQuantity)
    }

    private fun showReview() {
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.content_empty, Toast.LENGTH_SHORT).show()
            return
        }
        screen = Screen.REVIEW
        editingProductId = null
        reviewIndex = reviewIndex.coerceIn(0, items.lastIndex)
        showPanel(binding.reviewPanel)
        renderReviewItem()
    }

    private fun renderReviewItem() {
        val item = items.getOrNull(reviewIndex) ?: return
        val product = catalog.firstOrNull { it.id == item.productId }
        val name = product?.let { ProductCatalog.displayName(it, currentLanguage()) } ?: item.reportNameEn
        val unit = if (item.quantity == 1) item.unitSingular else item.unitPlural
        binding.tvReviewPosition.text = getString(R.string.content_position, reviewIndex + 1, items.size)
        binding.tvReviewItem.text = getString(R.string.content_item_format, name, item.quantity, unit)
    }

    private fun addSelectedItem() {
        val product = selectedProduct ?: return
        val newItem = OrderContentItem(
            productId = product.id,
            categoryId = product.categoryId,
            reportNameEn = product.reportNameEn,
            quantity = quantity,
            unitSingular = product.unitSingular,
            unitPlural = product.unitPlural
        )
        val existingIndex = items.indexOfFirst { it.productId == product.id }
        if (existingIndex >= 0) {
            val replacementQuantity = if (editingProductId == product.id) {
                quantity
            } else {
                items[existingIndex].quantity + quantity
            }
            items[existingIndex] = newItem.copy(quantity = replacementQuantity)
        } else {
            items += newItem
        }
        editingProductId = null
        showCategories()
    }

    private fun setQuantity(value: Int) {
        quantity = value.coerceIn(1, MAX_QUANTITY)
        _binding?.tvQuantity?.text = quantity.toString()
    }

    private fun showManualQuantityDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(quantity.toString())
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.manual_input)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let(::setQuantity)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startVoiceQuantityInput() {
        val locale = resources.configuration.locales[0]
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_quantity_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            voiceQuantityLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.voice_recognition_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCustomProductDialog() {
        val localName = EditText(requireContext()).apply { hint = getString(R.string.custom_product_local_name) }
        val englishName = EditText(requireContext()).apply { hint = getString(R.string.custom_product_english_name) }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(localName)
            addView(englishName)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_product)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val local = localName.text.toString().trim()
                val english = englishName.text.toString().trim()
                if (local.isBlank() || english.isBlank()) {
                    Toast.makeText(requireContext(), R.string.custom_product_names_required, Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                    selectCustomProductUnit(local, english)
                }
            }
        }
        dialog.show()
    }

    private fun selectCustomProductUnit(localName: String, englishName: String) {
        val units = listOf(
            "pcs" to ("pc" to "pcs"),
            "pairs" to ("pair" to "pairs"),
            "packages" to ("package" to "packages"),
            "boxes" to ("box" to "boxes"),
            "bottles" to ("bottle" to "bottles")
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_unit)
            .setItems(units.map { it.first }.toTypedArray()) { _, which ->
                val language = currentLanguage().substringBefore('-')
                val product = ProductCatalogItem(
                    id = "custom_${UUID.randomUUID()}",
                    categoryId = ProductCatalog.CATEGORY_OTHER,
                    names = mapOf(language to localName, "en" to englishName),
                    reportNameEn = englishName,
                    unitSingular = units[which].second.first,
                    unitPlural = units[which].second.second,
                    sortOrder = 1000,
                    custom = true
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { AppDependencies.firebaseManager.saveProductCatalogItem(product) }
                    catalog = (catalog + product).distinctBy { it.id }
                    showQuantity(product)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveAndFinish() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AppDependencies.firebaseManager.saveOrderContent(
                    OrderContent(
                        tracking = tracking,
                        items = items.sortedBy { it.reportNameEn.lowercase(Locale.ENGLISH) },
                        status = "draft",
                        updatedAt = Timestamp.now()
                    )
                )
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_TRACKING to tracking, RESULT_ITEM_COUNT to items.size)
                )
                Toast.makeText(requireContext(), R.string.content_saved, Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (_: Exception) {
                setLoading(false)
                Toast.makeText(requireContext(), R.string.content_save_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSummary() {
        binding.tvContentSummary.text = if (items.isEmpty()) {
            getString(R.string.content_empty)
        } else {
            getString(R.string.content_summary, items.size, items.sumOf { it.quantity })
        }
        binding.btnReviewContents.isEnabled = items.isNotEmpty()
    }

    private fun fillGrid(choices: List<Choice>) {
        binding.gridContentChoices.removeAllViews()
        choices.take(6).forEachIndexed { index, choice ->
            val button = MaterialButton(requireContext()).apply {
                text = choice.title
                textSize = 17f
                isAllCaps = false
                setOnClickListener { choice.action() }
            }
            val row = index / 2
            val column = index % 2
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(6, 6, 6, 6)
            }
            binding.gridContentChoices.addView(button, params)
        }
    }

    private fun showPanel(visible: View) {
        binding.gridContentChoices.visibility = if (visible === binding.gridContentChoices) View.VISIBLE else View.GONE
        binding.quantityPanel.visibility = if (visible === binding.quantityPanel) View.VISIBLE else View.GONE
        binding.reviewPanel.visibility = if (visible === binding.reviewPanel) View.VISIBLE else View.GONE
    }

    private fun setLoading(loading: Boolean) {
        _binding?.contentProgress?.visibility = if (loading) View.VISIBLE else View.GONE
        _binding?.btnContentFinish?.isEnabled = !loading
    }

    private fun currentLanguage(): String = resources.configuration.locales[0].toLanguageTag().lowercase(Locale.ROOT)

    private fun parseQuantity(value: String): Int? {
        Regex("\\d+").find(value)?.value?.toIntOrNull()?.let { return it.coerceIn(1, MAX_QUANTITY) }
        val normalized = value.lowercase(Locale.ROOT).trim()
        val words = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
            "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5
        )
        return words.entries.firstOrNull { (word, _) -> normalized.contains(word) }?.value
    }

    private fun OrderContentItem.toCatalogItem() = ProductCatalogItem(
        id = productId,
        categoryId = categoryId,
        names = mapOf("en" to reportNameEn),
        reportNameEn = reportNameEn,
        unitSingular = unitSingular,
        unitPlural = unitPlural,
        custom = true
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class Choice(val title: String, val action: () -> Unit)
    private enum class Screen { CATEGORIES, PRODUCTS, QUANTITY, REVIEW }

    companion object {
        const val REQUEST_KEY = "order_content_saved"
        const val RESULT_TRACKING = "tracking"
        const val RESULT_ITEM_COUNT = "item_count"
        private const val ARG_TRACKING = "tracking"
        private const val MAX_QUANTITY = 9999

        fun newInstance(tracking: String) = OrderContentDialogFragment().apply {
            arguments = bundleOf(ARG_TRACKING to tracking)
        }
    }
}
