package com.ocr.fastscan.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ocr.fastscan.data.model.BoxEntry
import com.ocr.fastscan.data.model.BoxInventoryReport
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.OrderContent
import com.ocr.fastscan.data.model.ProductCatalogItem
import com.ocr.fastscan.data.model.SearchResult
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.google.firebase.firestore.toObject
import java.util.Date
import java.util.Locale

class FirebaseManager(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val preferences = androidx.preference.PreferenceManager
        .getDefaultSharedPreferences(context.applicationContext)

    private val warehouseDocument
        get() = db.collection("warehouses").document(currentWarehouseId())

    private val shipmentsCollection
        get() = warehouseDocument.collection("shipments")

    private val boxEntriesCollection
        get() = warehouseDocument.collection("box_entries")

    private fun currentWarehouseId(): String = preferences
        .getString(PREF_WAREHOUSE_ID, DEFAULT_WAREHOUSE_ID)
        .orEmpty()
        .trim()
        .ifBlank { DEFAULT_WAREHOUSE_ID }

    fun saveShipment(
        shipment: FirestoreShipment,    // ← основной параметр — объект с данными
        onSuccess: () -> Unit,          // колбэк успеха
        onFailure: (Exception) -> Unit) // колбэк ошибки
    {
        shipmentsCollection
            .document(shipment.trackingNumber) // Устанавливаем ID документа равным номеру трека
            .set(shipment) // .set перезапишет данные, если ID уже существует
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Data saved/updated in Firestore: ${shipment.trackingNumber}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving data", e)
                onFailure(e)
            }
    }
    fun saveBoxEntry
                (entry: BoxEntry,
                 onSuccess: () -> Unit = {},          // колбэк успеха
                 onFailure: (Exception) -> Unit = {}     // колбэк ошибки)
    )    {
        val collection = boxEntriesCollection
            // Используем трек как ID документа — если запись уже есть, она перезапишется
            collection.document(entry.orderTracking)  // ← трек как document ID
            .set(entry)
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Box entry saved: order ${entry.orderTracking} in box ${entry.boxTracking}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving box entry", e)
                onFailure(e)
            }
    }

    suspend fun findOrderEverywhere(track: String): SearchResult {
        return try {
            // Выполняем запросы
            var shipmentSnap = shipmentsCollection
                .whereEqualTo("trackingNumber", track)
                .limit(1)
                .get()
                .await()

            var boxSnap = boxEntriesCollection
                .whereEqualTo("orderTracking", track)
                .limit(1)
                .get()
                .await()

            if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID && shipmentSnap.isEmpty) {
                shipmentSnap = db.collection("shipments")
                    .whereEqualTo("trackingNumber", track)
                    .limit(1)
                    .get()
                    .await()
            }
            if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID && boxSnap.isEmpty) {
                boxSnap = db.collection("box_entries")
                    .whereEqualTo("orderTracking", track)
                    .limit(1)
                    .get()
                    .await()
            }

            SearchResult(
                shipment = shipmentSnap.documents.firstOrNull()?.toObject<FirestoreShipment>(),
                boxEntry = boxSnap.documents.firstOrNull()?.toObject<BoxEntry>()
            )
        } catch (e: Exception) {
            // Если ошибка в запросе, возвращаем пустой результат
            Log.e("FirebaseSearch", "Ошибка конвертации: ${e.message}", e)
            SearchResult(null, null)
        }
    }
    // Получить ВСЕ зарегистрированные заказы
    suspend fun getAllShipments(): List<FirestoreShipment> {
        return try {
            val scoped = shipmentsCollection.get().await().toObjects(FirestoreShipment::class.java)
            val legacy = if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID) {
                db.collection("shipments").get().await().toObjects(FirestoreShipment::class.java)
            } else emptyList()
            (scoped + legacy).distinctBy { normalizeTracking(it.trackingNumber) }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Ошибка получения shipments", e)
            emptyList()
        }
    }

    // Получить ВСЕ записи об упаковке в коробки
    suspend fun getAllBoxEntries(): List<BoxEntry> {
        return try {
            val scoped = boxEntriesCollection.get().await().toObjects(BoxEntry::class.java)
            val legacy = if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID) {
                db.collection("box_entries").get().await().toObjects(BoxEntry::class.java)
            } else emptyList()
            (scoped + legacy).distinctBy { normalizeTracking(it.orderTracking) }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Ошибка получения box_entries", e)
            emptyList()
        }
    }
    // Получить заказы (shipments) после определённой даты
    suspend fun getShipmentsAfter(date: Date): List<FirestoreShipment> {
        return try {
            val scoped = shipmentsCollection
                .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(date))
                .get()
                .await()
                .toObjects(FirestoreShipment::class.java)
            val legacy = if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID) {
                db.collection("shipments")
                    .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(date))
                    .get().await().toObjects(FirestoreShipment::class.java)
            } else emptyList()
            (scoped + legacy).distinctBy { normalizeTracking(it.trackingNumber) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Получить записи об упаковке после определённой даты
    suspend fun getBoxEntriesAfter(date: Date): List<BoxEntry> {
        return try {
            val scoped = boxEntriesCollection
                .whereGreaterThanOrEqualTo("date", com.google.firebase.Timestamp(date))
                .get()
                .await()
                .toObjects(BoxEntry::class.java)
            val legacy = if (currentWarehouseId() == DEFAULT_WAREHOUSE_ID) {
                db.collection("box_entries")
                    .whereGreaterThanOrEqualTo("date", com.google.firebase.Timestamp(date))
                    .get().await().toObjects(BoxEntry::class.java)
            } else emptyList()
            (scoped + legacy).distinctBy { normalizeTracking(it.orderTracking) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getProductCatalog(): List<ProductCatalogItem> {
        return try {
            warehouseDocument.collection("product_catalog")
                .get()
                .await()
                .toObjects(ProductCatalogItem::class.java)
                .filter { it.id.isNotBlank() && it.reportNameEn.isNotBlank() }
                .sortedWith(compareBy(ProductCatalogItem::categoryId, ProductCatalogItem::sortOrder))
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to load product catalog", e)
            emptyList()
        }
    }

    suspend fun saveProductCatalogItem(item: ProductCatalogItem) {
        require(item.id.isNotBlank())
        warehouseDocument.collection("product_catalog")
            .document(safeDocumentId(item.id))
            .set(item, SetOptions.merge())
            .await()
    }

    suspend fun seedProductCatalog(items: List<ProductCatalogItem>) {
        if (items.isEmpty()) return
        val batch = db.batch()
        items.forEach { item ->
            val reference = warehouseDocument.collection("product_catalog")
                .document(safeDocumentId(item.id))
            batch.set(reference, item, SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun getOrderContent(tracking: String): OrderContent? {
        if (tracking.isBlank()) return null
        return try {
            warehouseDocument.collection("order_contents")
                .document(safeDocumentId(normalizeTracking(tracking)))
                .get()
                .await()
                .toObject<OrderContent>()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to load contents for $tracking", e)
            null
        }
    }

    suspend fun getOrderContents(trackings: Collection<String>): Map<String, OrderContent> {
        val normalizedTrackings = trackings
            .asSequence()
            .map(::normalizeTracking)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        return coroutineScope {
            normalizedTrackings.map { tracking ->
                async {
                    warehouseDocument.collection("order_contents")
                        .document(safeDocumentId(tracking))
                        .get()
                        .await()
                        .toObject<OrderContent>()
                }
            }.awaitAll()
                .filterNotNull()
                .associateBy { normalizeTracking(it.tracking) }
        }
    }

    suspend fun saveOrderContent(content: OrderContent) {
        require(content.tracking.isNotBlank())
        val normalized = content.copy(tracking = normalizeTracking(content.tracking))
        warehouseDocument.collection("order_contents")
            .document(safeDocumentId(normalized.tracking))
            .set(normalized)
            .await()
    }

    suspend fun confirmOrderContent(tracking: String) {
        if (tracking.isBlank()) return
        val reference = warehouseDocument.collection("order_contents")
            .document(safeDocumentId(normalizeTracking(tracking)))
        if (reference.get().await().exists()) {
            reference.update("status", "confirmed").await()
        }
    }

    suspend fun saveBoxInventoryReports(reports: List<BoxInventoryReport>) {
        if (reports.isEmpty()) return
        reports.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { report ->
                val reportId = safeDocumentId("${report.partyGuid}:${report.boxTracking}")
                val ref = warehouseDocument.collection("box_inventory_reports").document(reportId)
                batch.set(ref, report)
            }
            batch.commit().await()
        }
    }

    suspend fun getBoxInventoryReports(partyGuid: String): List<BoxInventoryReport> {
        if (partyGuid.isBlank()) return emptyList()
        return warehouseDocument.collection("box_inventory_reports")
            .whereEqualTo("partyGuid", partyGuid)
            .get()
            .await()
            .toObjects(BoxInventoryReport::class.java)
            .sortedBy { it.boxTracking }
    }

    companion object {
        const val PREF_WAREHOUSE_ID = "firebase_warehouse_id"
        const val DEFAULT_WAREHOUSE_ID = "es-main"
        private const val FIRESTORE_BATCH_LIMIT = 450

        fun normalizeTracking(value: String): String = value.trim().uppercase(Locale.ROOT)

        private fun safeDocumentId(value: String): String = Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
