package com.ocr.fastscan.data

import com.ocr.fastscan.data.model.ProductCatalogItem

object ProductCatalog {
    const val CATEGORY_CLOTHING = "clothing"
    const val CATEGORY_FOOTWEAR = "footwear"
    const val CATEGORY_ELECTRONICS = "electronics"
    const val CATEGORY_FOOD = "food"
    const val CATEGORY_BAGS = "bags"
    const val CATEGORY_OTHER = "other"

    val defaultItems: List<ProductCatalogItem> = listOf(
        item("jacket", CATEGORY_CLOTHING, "Куртка", "Jacket", "Chaqueta", 10),
        item("jeans", CATEGORY_CLOTHING, "Джинсы", "Jeans", "Vaqueros", 20),
        item("trousers", CATEGORY_CLOTHING, "Брюки", "Trousers", "Pantalones", 30),
        item("tshirt", CATEGORY_CLOTHING, "Футболка", "T-shirt", "Camiseta", 40),
        item("sweater", CATEGORY_CLOTHING, "Свитер", "Sweater", "Jersey", 50),
        item("hat", CATEGORY_CLOTHING, "Шапка", "Hat", "Gorro", 60),
        item("shoes", CATEGORY_FOOTWEAR, "Обувь", "Shoes", "Calzado", 10, "pair", "pairs"),
        item("electronics", CATEGORY_ELECTRONICS, "Электроника", "Electronics", "Electrónica", 10),
        item("alcohol", CATEGORY_FOOD, "Алкоголь", "Alcohol", "Alcohol", 10, "bottle", "bottles"),
        item("cheese", CATEGORY_FOOD, "Сыр", "Cheese", "Queso", 20, "package", "packages"),
        item("jamon", CATEGORY_FOOD, "Хамон", "Jamon", "Jamón", 30, "package", "packages"),
        item("candies", CATEGORY_FOOD, "Конфеты", "Candies", "Dulces", 40, "package", "packages"),
        item("bags", CATEGORY_BAGS, "Сумки", "Bags", "Bolsos", 10),
        item("toys", CATEGORY_OTHER, "Игрушки", "Toys", "Juguetes", 10),
        item("documents", CATEGORY_OTHER, "Документы", "Documents", "Documentos", 20),
        item("cosmetics", CATEGORY_OTHER, "Косметика", "Cosmetics", "Cosméticos", 30, "box", "boxes"),
        item("costume_jewelry", CATEGORY_OTHER, "Бижутерия", "Costume jewelry", "Bisutería", 40, "package", "packages")
    )

    fun displayName(item: ProductCatalogItem, language: String): String =
        item.names[language]
            ?: item.names[language.substringBefore('-')]
            ?: item.names["en"]
            ?: item.reportNameEn

    private fun item(
        id: String,
        categoryId: String,
        ru: String,
        en: String,
        es: String,
        sortOrder: Int,
        singular: String = "pc",
        plural: String = "pcs"
    ) = ProductCatalogItem(
        id = id,
        categoryId = categoryId,
        names = mapOf("ru" to ru, "en" to en, "es" to es),
        reportNameEn = en,
        unitSingular = singular,
        unitPlural = plural,
        sortOrder = sortOrder
    )
}
