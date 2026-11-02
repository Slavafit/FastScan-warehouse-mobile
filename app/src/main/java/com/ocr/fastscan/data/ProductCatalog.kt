package com.ocr.fastscan.data

import com.ocr.fastscan.data.model.ProductCatalogItem

object ProductCatalog {
    const val CATEGORY_CLOTHING = "clothing"
    const val CATEGORY_FOOTWEAR = "footwear"
    const val CATEGORY_BAGS_ACCESSORIES = "bags_accessories"
    const val CATEGORY_BEAUTY = "beauty"
    const val CATEGORY_TOYS_SPORTS = "toys_sports"
    const val CATEGORY_ELECTRONICS_APPLIANCES = "electronics_appliances"
    const val CATEGORY_HOME = "home"
    const val CATEGORY_HEALTH = "health"
    const val CATEGORY_FOOD_DRINKS = "food_drinks"

    val categoryIds = setOf(
        CATEGORY_CLOTHING, CATEGORY_FOOTWEAR, CATEGORY_BAGS_ACCESSORIES,
        CATEGORY_BEAUTY, CATEGORY_TOYS_SPORTS, CATEGORY_ELECTRONICS_APPLIANCES,
        CATEGORY_HOME, CATEGORY_HEALTH, CATEGORY_FOOD_DRINKS
    )

    val defaultItems: List<ProductCatalogItem> = listOf(
        item("clothing_tops_shirts", CATEGORY_CLOTHING,
            "Футболки, майки, топы, рубашки и блузки",
            "T-shirts, tank tops, tops, shirts and blouses",
            "Camisetas, camisas y tops", 10),
        item("clothing_sweaters_hoodies", CATEGORY_CLOTHING,
            "Свитеры, кардиганы, худи", "Sweaters, cardigans and hoodies",
            "Jerséis y sudaderas", 20),
        item("clothing_trousers_shorts", CATEGORY_CLOTHING,
            "Брюки, джинсы, шорты", "Trousers, jeans and shorts",
            "Pantalones y pantalones cortos", 30),
        item("clothing_dresses_skirts", CATEGORY_CLOTHING,
            "Платья, юбки, комбинезоны", "Dresses, skirts and jumpsuits",
            "Vestidos, faldas y monos", 40),
        item("clothing_coats_jackets", CATEGORY_CLOTHING,
            "Куртки, пальто, пуховики", "Jackets, coats and down jackets",
            "Abrigos y chaquetas", 50),
        item("clothing_suits_blazers", CATEGORY_CLOTHING,
            "Костюмы и пиджаки", "Suits and blazers", "Trajes y americanas", 60),
        item("clothing_sportswear", CATEGORY_CLOTHING,
            "Спортивная одежда", "Sportswear", "Ropa deportiva", 70),
        item("clothing_underwear_sleepwear", CATEGORY_CLOTHING,
            "Нижнее бельё, пижамы, купальники", "Underwear, pajamas and swimwear",
            "Ropa interior, pijamas y bañadores", 80),
        item("clothing_baby", CATEGORY_CLOTHING,
            "Одежда для младенцев", "Baby clothing", "Ropa para bebés", 90),

        item("footwear", CATEGORY_FOOTWEAR,
            "Обувь", "Footwear", "Calzado", 10, "pair", "pairs"),

        item("bags_luggage_wallets", CATEGORY_BAGS_ACCESSORIES,
            "Сумки, чемоданы, кошельки", "Bags, suitcases and wallets",
            "Bolsos, maletas y carteras", 10),
        item("headwear", CATEGORY_BAGS_ACCESSORIES,
            "Головные уборы", "Hats and other headwear",
            "Sombreros y demás tocados", 20),
        item("costume_jewelry", CATEGORY_BAGS_ACCESSORIES,
            "Бижутерия", "Costume jewelry", "Bisutería", 30),
        item("watches", CATEGORY_BAGS_ACCESSORIES,
            "Часы", "Watches", "Relojes", 40),
        item("glasses", CATEGORY_BAGS_ACCESSORIES,
            "Очки", "Glasses", "Gafas", 50),

        item("beauty_cosmetics_skin", CATEGORY_BEAUTY,
            "Косметика и уход за кожей", "Cosmetics and skin care products",
            "Cosméticos y productos para el cuidado de la piel", 10, "package", "packages"),
        item("beauty_perfume", CATEGORY_BEAUTY,
            "Парфюмерия", "Perfumes and eau de toilette",
            "Perfumes y aguas de tocador", 20, "bottle", "bottles"),
        item("beauty_hair", CATEGORY_BEAUTY,
            "Шампуни и средства для волос", "Hair care products",
            "Productos para el cuidado del cabello", 30, "bottle", "bottles"),

        item("toys", CATEGORY_TOYS_SPORTS,
            "Игрушки", "Toys", "Juguetes", 10),
        item("sporting_goods", CATEGORY_TOYS_SPORTS,
            "Спортивные товары", "Sporting goods", "Artículos de deporte", 20),

        item("electronics", CATEGORY_ELECTRONICS_APPLIANCES,
            "Электроника", "Electronic devices", "Aparatos electrónicos", 10),
        item("household_appliances", CATEGORY_ELECTRONICS_APPLIANCES,
            "Бытовая техника", "Household appliances", "Electrodomésticos", 20),

        item("home_textiles", CATEGORY_HOME,
            "Текстиль для дома", "Home textiles", "Artículos textiles para el hogar", 10),
        item("tableware_kitchenware", CATEGORY_HOME,
            "Посуда", "Tableware and kitchen utensils",
            "Vajilla y utensilios de cocina", 20),

        item("vitamins_supplements", CATEGORY_HEALTH,
            "Витамины и БАДы", "Vitamins and dietary supplements",
            "Vitaminas y complementos alimenticios", 10, "package", "packages"),

        item("food_products", CATEGORY_FOOD_DRINKS,
            "Продукты питания", "Food products", "Productos alimenticios",
            10, "package", "packages"),
        item("beverages", CATEGORY_FOOD_DRINKS,
            "Напитки", "Beverages", "Bebidas", 20, "bottle", "bottles")
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
