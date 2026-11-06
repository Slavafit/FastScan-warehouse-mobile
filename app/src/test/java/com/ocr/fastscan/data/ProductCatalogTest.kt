package com.ocr.fastscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun `catalog contains the approved groups and products`() {
        assertEquals(10, ProductCatalog.categoryIds.size)
        assertEquals(31, ProductCatalog.defaultItems.size)
        assertEquals(
            ProductCatalog.defaultItems.size,
            ProductCatalog.defaultItems.map { it.id }.distinct().size
        )
        assertTrue(ProductCatalog.defaultItems.all { it.categoryId in ProductCatalog.categoryIds })
        assertTrue(ProductCatalog.defaultItems.all { item ->
            listOf("ru", "es", "en").all { language -> item.names[language].isNullOrBlank().not() }
        })
    }

    @Test
    fun `customs report names remain English`() {
        val coats = ProductCatalog.defaultItems.single { it.id == "clothing_coats_jackets" }
        val cosmetics = ProductCatalog.defaultItems.single { it.id == "beauty_cosmetics_skin" }
        val food = ProductCatalog.defaultItems.single { it.id == "food_products" }
        val documents = ProductCatalog.defaultItems.single { it.id == "documents" }
        val vinylRecords = ProductCatalog.defaultItems.single { it.id == "vinyl_records" }

        assertEquals("Jackets, coats and down jackets", coats.reportNameEn)
        assertEquals("Cosmetics and skin care products", cosmetics.reportNameEn)
        assertEquals("Food products", food.reportNameEn)
        assertEquals("Documents", documents.reportNameEn)
        assertEquals("Vinyl records", vinylRecords.reportNameEn)
    }
}
