package com.ocr.fastscan.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
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
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private var productPage = 0

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

    private val voiceSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val candidates = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        handleVoiceSelection(candidates)
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
            val defaultIds = ProductCatalog.defaultItems.mapTo(mutableSetOf(), ProductCatalogItem::id)
            if (remoteCatalog.isEmpty() || defaultIds.any { id -> remoteCatalog.none { it.id == id } }) {
                runCatching { manager.seedProductCatalog(ProductCatalog.defaultItems) }
            }
            val customItems = remoteCatalog
                .filter(ProductCatalogItem::custom)
                .map(::migrateCustomProductCategory)
                .filter { it.categoryId in ProductCatalog.categoryIds }
            catalog = (ProductCatalog.defaultItems + customItems)
                .distinctBy { it.id }
                .sortedWith(compareBy(ProductCatalogItem::categoryId, ProductCatalogItem::sortOrder))
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
                    val categoryId = selectedCategory.orEmpty()
                    when {
                        editingProductId != null -> showReview()
                        opensQuantityDirectly(categoryId) -> showCategories()
                        else -> showProducts(categoryId)
                    }
                }
            }
        }
        binding.btnReviewContents.setOnClickListener { showReview() }
        binding.btnContentFinish.setOnClickListener { saveAndFinish() }
        binding.btnVoiceSelection.setOnClickListener { startVoiceSelectionInput() }
        binding.btnNewProduct.setOnClickListener { showCustomProductDialog() }
        binding.btnProductPrevious.setOnClickListener {
            if (screen == Screen.PRODUCTS && productPage > 0) {
                productPage--
                showProducts(selectedCategory.orEmpty())
            }
        }
        binding.btnProductNext.setOnClickListener {
            if (screen == Screen.PRODUCTS) {
                productPage++
                showProducts(selectedCategory.orEmpty())
            }
        }
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
        fillGrid(
            choices = categoryDefinitions().map { category ->
                Choice(category.title) { openCategory(category.id) }
            },
            columns = 2,
            rows = 5,
            minTextSizeSp = 11,
            maxTextSizeSp = 16,
            spanLastItem = true
        )
        updateSummary()
    }

    private fun openCategory(categoryId: String) {
        selectedCategory = categoryId
        productPage = 0
        val products = catalog
            .filter { it.categoryId == categoryId }
            .sortedBy { it.sortOrder }
        if (opensQuantityDirectly(categoryId)) {
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
        val products = catalog
            .filter { it.categoryId == categoryId }
            .sortedBy { it.sortOrder }
        val pageSize = productPageSize(categoryId)
        val pageCount = ((products.size - 1) / pageSize + 1).coerceAtLeast(1)
        productPage = productPage.coerceIn(0, pageCount - 1)
        val choices = products
            .drop(productPage * pageSize)
            .take(pageSize)
            .map { product ->
                Choice(ProductCatalog.displayName(product, language)) { showQuantity(product) }
            }
        updatePagingActions(productPage, pageCount, showNewProduct = true)
        val showFiveRows = pageSize == CLOTHING_CHOICES_PER_PAGE
        fillGrid(
            choices = choices,
            columns = 2,
            rows = if (showFiveRows) 5 else 3,
            minTextSizeSp = if (showFiveRows) 11 else 12,
            maxTextSizeSp = if (showFiveRows) 16 else 17,
            spanLastItem = showFiveRows
        )
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

    private fun startVoiceSelectionInput() {
        if (screen != Screen.CATEGORIES && screen != Screen.PRODUCTS) return
        val locale = resources.configuration.locales[0]
        val prompt = if (screen == Screen.CATEGORIES) {
            R.string.voice_category_prompt
        } else {
            R.string.voice_product_prompt
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            voiceSelectionLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.voice_recognition_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleVoiceSelection(spokenCandidates: List<String>) {
        val options = when (screen) {
            Screen.CATEGORIES -> categoryDefinitions().map { category ->
                VoiceOption(category.id, category.title, category.aliases) {
                    openCategory(category.id)
                }
            }
            Screen.PRODUCTS -> catalog
                .filter { it.categoryId == selectedCategory }
                .sortedBy { it.sortOrder }
                .map { product ->
                    VoiceOption(
                        id = product.id,
                        title = ProductCatalog.displayName(product, currentLanguage()),
                        aliases = product.names.values + product.reportNameEn
                    ) { showQuantity(product) }
                }
            else -> emptyList()
        }
        val optionById = options.associateBy { it.id }
        val matches = VoiceChoiceMatcher.bestMatches(
            spokenCandidates,
            options.associate { it.id to it.aliases }
        ).mapNotNull(optionById::get)

        when (matches.size) {
            0 -> Toast.makeText(
                requireContext(),
                R.string.voice_selection_not_recognized,
                Toast.LENGTH_SHORT
            ).show()
            1 -> matches.first().action()
            else -> AlertDialog.Builder(requireContext())
                .setTitle(R.string.voice_selection_ambiguous)
                .setItems(matches.map { it.title }.toTypedArray()) { _, index ->
                    matches[index].action()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun categoryDefinitions(): List<CategoryDefinition> = listOf(
        CategoryDefinition(
            ProductCatalog.CATEGORY_CLOTHING,
            getString(R.string.category_clothing),
            listOf("одежда", "clothing", "clothes", "ropa")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_FOOTWEAR,
            getString(R.string.category_footwear),
            listOf("обувь", "footwear", "shoes", "calzado", "zapatos")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_BAGS_ACCESSORIES,
            getString(R.string.category_bags_accessories),
            listOf("сумки и аксессуары", "bags and accessories", "bolsos y accesorios")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_BEAUTY,
            getString(R.string.category_beauty),
            listOf(
                "красота и уход", "косметика", "beauty and personal care",
                "beauty", "belleza y cuidado personal", "belleza"
            )
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_TOYS_SPORTS,
            getString(R.string.category_toys_sports),
            listOf("игрушки и спорт", "toys and sports", "juguetes y deporte")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_ELECTRONICS_APPLIANCES,
            getString(R.string.category_electronics_appliances),
            listOf(
                "электроника и техника", "электроника", "electronics and appliances",
                "electronics", "electrónica y electrodomésticos", "electronica"
            )
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_HOME,
            getString(R.string.category_home),
            listOf("товары для дома", "для дома", "home goods", "home", "artículos para el hogar")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_HEALTH,
            getString(R.string.category_health),
            listOf("здоровье", "health", "salud")
        ),
        CategoryDefinition(
            ProductCatalog.CATEGORY_FOOD_DRINKS,
            getString(R.string.category_food_drinks),
            listOf(
                "продукты питания и напитки", "продукты", "food and beverages",
                "food", "alimentos y bebidas", "alimentos"
            )
        )
    ).map { it.copy(aliases = it.aliases + it.title) }

    private fun migrateCustomProductCategory(item: ProductCatalogItem): ProductCatalogItem {
        val categoryId = when (item.categoryId) {
            "bags" -> ProductCatalog.CATEGORY_BAGS_ACCESSORIES
            "electronics" -> ProductCatalog.CATEGORY_ELECTRONICS_APPLIANCES
            "food" -> ProductCatalog.CATEGORY_FOOD_DRINKS
            else -> item.categoryId
        }
        return if (categoryId == item.categoryId) item else item.copy(categoryId = categoryId)
    }

    private fun showCustomProductDialog() {
        if (selectedCategory.isNullOrBlank()) return
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
        val categoryId = selectedCategory ?: return
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
                    categoryId = categoryId,
                    names = mapOf(language to localName, "en" to englishName),
                    reportNameEn = englishName,
                    unitSingular = units[which].second.first,
                    unitPlural = units[which].second.second,
                    sortOrder = (catalog.filter { it.categoryId == categoryId }
                        .maxOfOrNull { it.sortOrder } ?: 0) + 10,
                    custom = true
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        AppDependencies.firebaseManager.saveProductCatalogItem(product)
                        catalog = (catalog + product).distinctBy { it.id }
                        productPage = catalog
                            .filter { it.categoryId == categoryId }
                            .sortedBy { it.sortOrder }
                            .indexOfFirst { it.id == product.id }
                            .coerceAtLeast(0) / productPageSize(categoryId)
                        showQuantity(product)
                    } catch (e: Exception) {
                        Log.e("OrderContent", "Failed to save custom product", e)
                        Toast.makeText(
                            requireContext(),
                            R.string.custom_product_save_error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
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
            } catch (e: Exception) {
                Log.e("OrderContent", "Failed to save contents for $tracking", e)
                setLoading(false)
                val detail = (e as? FirebaseFirestoreException)?.code?.name
                    ?: e.localizedMessage
                    ?: e.javaClass.simpleName
                Toast.makeText(
                    requireContext(),
                    getString(R.string.content_save_error_detail, detail.take(120)),
                    Toast.LENGTH_LONG
                ).show()
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

    private fun fillGrid(
        choices: List<Choice>,
        columns: Int = 2,
        rows: Int = 3,
        minTextSizeSp: Int = 12,
        maxTextSizeSp: Int = 17,
        spanLastItem: Boolean = false
    ) {
        binding.gridContentChoices.removeAllViews()
        binding.gridContentChoices.columnCount = columns
        binding.gridContentChoices.rowCount = rows
        choices.take(columns * rows).forEachIndexed { index, choice ->
            val button = MaterialButton(requireContext()).apply {
                text = choice.title
                isAllCaps = false
                gravity = Gravity.CENTER
                maxLines = 3
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    minTextSizeSp,
                    maxTextSizeSp,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                setOnClickListener { choice.action() }
            }
            val isSpanningLastItem = spanLastItem &&
                index == choices.lastIndex &&
                choices.size % columns != 0
            val row = index / columns
            val column = if (isSpanningLastItem) 0 else index % columns
            val columnSpan = if (isSpanningLastItem) columns else 1
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, columnSpan, 1f)
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
        binding.btnVoiceSelection.visibility =
            if (visible === binding.gridContentChoices) View.VISIBLE else View.GONE
        binding.productActions.visibility =
            if (visible === binding.gridContentChoices && screen == Screen.PRODUCTS) View.VISIBLE else View.GONE
        binding.quantityPanel.visibility = if (visible === binding.quantityPanel) View.VISIBLE else View.GONE
        binding.reviewPanel.visibility = if (visible === binding.reviewPanel) View.VISIBLE else View.GONE
    }

    private fun updatePagingActions(currentPage: Int, pageCount: Int, showNewProduct: Boolean) {
        binding.btnNewProduct.visibility = if (showNewProduct) View.VISIBLE else View.GONE
        binding.btnProductPrevious.isEnabled = currentPage > 0
        binding.btnProductNext.isEnabled = currentPage < pageCount - 1
        binding.btnProductPrevious.visibility = if (pageCount > 1) View.VISIBLE else View.INVISIBLE
        binding.btnProductNext.visibility = if (pageCount > 1) View.VISIBLE else View.INVISIBLE
    }

    private fun productPageSize(categoryId: String): Int =
        if (categoryId == ProductCatalog.CATEGORY_CLOTHING) {
            CLOTHING_CHOICES_PER_PAGE
        } else {
            DEFAULT_CHOICES_PER_PAGE
        }

    private fun opensQuantityDirectly(categoryId: String): Boolean =
        categoryId == ProductCatalog.CATEGORY_FOOTWEAR &&
            catalog.count { it.categoryId == categoryId } == 1

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
    private data class CategoryDefinition(
        val id: String,
        val title: String,
        val aliases: List<String>
    )
    private data class VoiceOption(
        val id: String,
        val title: String,
        val aliases: Collection<String>,
        val action: () -> Unit
    )
    private enum class Screen { CATEGORIES, PRODUCTS, QUANTITY, REVIEW }

    companion object {
        const val REQUEST_KEY = "order_content_saved"
        const val RESULT_TRACKING = "tracking"
        const val RESULT_ITEM_COUNT = "item_count"
        private const val ARG_TRACKING = "tracking"
        private const val MAX_QUANTITY = 9999
        private const val DEFAULT_CHOICES_PER_PAGE = 6
        private const val CLOTHING_CHOICES_PER_PAGE = 10

        fun newInstance(tracking: String) = OrderContentDialogFragment().apply {
            arguments = bundleOf(ARG_TRACKING to tracking)
        }
    }
}
