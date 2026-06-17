package com.example.data

import kotlin.math.roundToInt

enum class FoodMeasureKind {
    LIQUID,
    FLATBREAD,
    DOSA_IDLI,
    RICE_GRAIN,
    FRUIT,
    BEVERAGE,
    PROTEIN,
    GENERIC
}

data class FoodServingUnit(
    val id: String,
    val label: String,
    /** Grams (or ml treated as grams for liquids) per 1 unit at quantity 1. */
    val gramsPerUnit: Double,
    val isDefault: Boolean = false
)

object FoodQuantityOptions {
    fun values(): List<Double> = buildList {
        addAll(listOf(0.25, 0.5, 0.75))
        var v = 1.0
        while (v <= 3.0) {
            add(v)
            v += 0.5
        }
        v = 4.0
        while (v <= 10.0) {
            add(v)
            v += 1.0
        }
        v = 15.0
        while (v <= 50.0) {
            add(v)
            v += 5.0
        }
        v = 60.0
        while (v <= 100.0) {
            add(v)
            v += 10.0
        }
        v = 150.0
        while (v <= 500.0) {
            add(v)
            v += 50.0
        }
        v = 600.0
        while (v <= 1000.0) {
            add(v)
            v += 100.0
        }
        v = 1500.0
        while (v <= 2000.0) {
            add(v)
            v += 500.0
        }
    }

    fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else "%.2f".format(value).trimEnd('0').trimEnd('.')
}

object FoodServingCatalog {

    fun isVolumeBased(food: FoodItem): Boolean =
        food.volumeBased || classify(food) in setOf(FoodMeasureKind.LIQUID, FoodMeasureKind.BEVERAGE)

    fun nutritionBasisLabel(food: FoodItem): String =
        if (isVolumeBased(food)) "100ml" else "100g"

    fun netWeightLabel(food: FoodItem, unit: FoodServingUnit, quantity: Double, grams: Int): String {
        if (shouldShowVolume(food, unit)) {
            val ml = quantity * unit.gramsPerUnit
            return "Net wt: ${FoodQuantityOptions.format(ml)} ml"
        }
        return "Net wt: ${String.format("%.1f", grams.toFloat())} g"
    }

    fun shouldShowVolume(food: FoodItem, unit: FoodServingUnit): Boolean =
        isVolumeBased(food) && (isVolumeUnit(unit) || unit.id == "grams")

    fun isVolumeUnit(unit: FoodServingUnit): Boolean =
        unit.id in VOLUME_UNIT_IDS

    /** Drop AI units with unrealistic sizes (e.g. "oreo" = 1 ml for a milkshake). */
    fun filterAiUnits(food: FoodItem, aiUnits: List<FoodServingUnit>): List<FoodServingUnit> {
        if (aiUnits.isEmpty()) return emptyList()
        val kind = classify(food)
        val baseLabels = unitsFor(food).map { it.label.lowercase() }.toSet()
        return aiUnits.filter { unit ->
            if (unit.gramsPerUnit <= 0) return@filter false
            if (baseLabels.contains(unit.label.lowercase())) return@filter false
            when (kind) {
                FoodMeasureKind.LIQUID, FoodMeasureKind.BEVERAGE ->
                    unit.gramsPerUnit >= 30.0 || unit.id == "ml" || unit.label.equals("ml", ignoreCase = true)
                else -> unit.gramsPerUnit >= 1.0
            }
        }.map { it.copy(isDefault = false) }
    }

    private val VOLUME_UNIT_IDS = setOf("ml", "cup", "glass", "liter", "tbsp", "tsp")

    private const val ML = 1.0
    private const val CUP_ML = 240.0
    private const val TBSP_ML = 15.0
    private const val TSP_ML = 5.0
    private const val GLASS_ML = 200.0
    private const val LITER_ML = 1000.0
    private const val OZ_G = 28.35

    fun classify(food: FoodItem): FoodMeasureKind {
        val n = food.name.lowercase()
        val aliases = food.aliases.joinToString(" ").lowercase()
        val blob = "$n $aliases"
        return when {
            blob.contains("milkshake") || blob.contains("milk shake") ||
                blob.contains("milk") || blob.contains("lassi") || blob.contains("chaas") ||
                blob.contains("buttermilk") || blob.contains("juice") || blob.contains("shake") ||
                blob.contains("smoothie") || blob.contains("soup") && !blob.contains("dry") ->
                FoodMeasureKind.LIQUID
            blob.contains("tea") || blob.contains("coffee") || blob.contains("chai") ->
                FoodMeasureKind.BEVERAGE
            blob.contains("roti") || blob.contains("chapati") || blob.contains("phulka") ||
                blob.contains("paratha") || blob.contains("naan") || blob.contains("kulcha") ||
                blob.contains("puri") || blob.contains("bhatura") ->
                FoodMeasureKind.FLATBREAD
            blob.contains("dosa") || blob.contains("idli") || blob.contains("uttapam") ||
                blob.contains("vada") || blob.contains("pesarattu") ->
                FoodMeasureKind.DOSA_IDLI
            blob.contains("rice") || blob.contains("biryani") || blob.contains("pulao") ||
                blob.contains("khichdi") || blob.contains("poha") || blob.contains("upma") ||
                blob.contains("quinoa") || blob.contains("oats") || blob.contains("cereal") ->
                FoodMeasureKind.RICE_GRAIN
            blob.contains("apple") || blob.contains("banana") || blob.contains("mango") ||
                blob.contains("fruit") || blob.contains("berry") ->
                FoodMeasureKind.FRUIT
            blob.contains("chicken") || blob.contains("egg") || blob.contains("fish") ||
                blob.contains("paneer") || blob.contains("tofu") || blob.contains("meat") ||
                blob.contains("prawn") || blob.contains("mutton") ->
                FoodMeasureKind.PROTEIN
            else -> FoodMeasureKind.GENERIC
        }
    }

    fun unitsFor(food: FoodItem, extra: List<FoodServingUnit> = emptyList()): List<FoodServingUnit> {
        val base = when (classify(food)) {
            FoodMeasureKind.LIQUID -> liquidUnits(food.name)
            FoodMeasureKind.BEVERAGE -> beverageUnits(food.name)
            FoodMeasureKind.FLATBREAD -> flatbreadUnits()
            FoodMeasureKind.DOSA_IDLI -> dosaIdliUnits(food.name)
            FoodMeasureKind.RICE_GRAIN -> riceUnits()
            FoodMeasureKind.FRUIT -> fruitUnits(food.name)
            FoodMeasureKind.PROTEIN -> proteinUnits()
            FoodMeasureKind.GENERIC -> genericUnits(food.name)
        }
        val merged = (base + filterAiUnits(food, extra)).distinctBy { it.id }
        return if (merged.any { it.isDefault }) merged else merged.mapIndexed { i, u ->
            if (i == 0) u.copy(isDefault = true) else u
        }
    }

    fun gramsFor(unit: FoodServingUnit, quantity: Double): Int =
        (unit.gramsPerUnit * quantity).coerceAtLeast(0.0).roundToInt()

    fun displayLabel(quantity: Double, unit: FoodServingUnit): String {
        val q = FoodQuantityOptions.format(quantity)
        val label = if (quantity > 1.0 && !unit.label.endsWith("s")) {
            when (unit.label) {
                "roti/chapati" -> "rotis/chapatis"
                "piece" -> "pieces"
                "serve" -> "serves"
                "cup" -> "cups"
                "glass" -> "glasses"
                "tablespoon" -> "tablespoons"
                "teaspoon" -> "teaspoons"
                "small" -> "small"
                "medium" -> "medium"
                "large" -> "large"
                else -> unit.label
            }
        } else unit.label
        return "$q $label"
    }

    fun defaultUnit(food: FoodItem, extra: List<FoodServingUnit> = emptyList()): FoodServingUnit =
        unitsFor(food, extra).firstOrNull { it.isDefault } ?: unitsFor(food, extra).first()

    /** Restore serving unit when editing a logged meal entry. */
    fun unitFromEntry(
        food: FoodItem,
        extra: List<FoodServingUnit>,
        servingLabel: String,
        servingQuantity: Float,
        weightGrams: Int
    ): FoodServingUnit {
        if (servingLabel.isBlank()) return defaultUnit(food, extra)
        val units = unitsFor(food, extra)
        units.find { it.label.equals(servingLabel, ignoreCase = true) }?.let { return it }
        val qty = servingQuantity.toDouble().takeIf { it > 0 } ?: 1.0
        val gramsPerUnit = weightGrams.toDouble() / qty
        return FoodServingUnit("saved_${servingLabel.hashCode()}", servingLabel, gramsPerUnit)
    }

    private fun liquidUnits(name: String): List<FoodServingUnit> {
        val lower = name.lowercase()
        val glassMl = when {
            lower.contains("shake") || lower.contains("smoothie") -> 350.0
            lower.contains("milk") && !lower.contains("powder") -> 250.0
            lower.contains("juice") || lower.contains("lassi") -> 240.0
            else -> GLASS_ML
        }
        return listOf(
            FoodServingUnit("glass", "glass", glassMl, isDefault = true),
            FoodServingUnit("cup", "cup", CUP_ML),
            FoodServingUnit("ml", "ml", ML),
            FoodServingUnit("tbsp", "tablespoon", TBSP_ML),
            FoodServingUnit("tsp", "teaspoon", TSP_ML),
            FoodServingUnit("liter", "liter", LITER_ML),
            FoodServingUnit("oz", "oz", OZ_G)
        )
    }

    private fun beverageUnits(name: String): List<FoodServingUnit> = listOf(
        FoodServingUnit("cup", "cup", 180.0, isDefault = true),
        FoodServingUnit("ml", "ml", ML),
        FoodServingUnit("glass", "glass", 200.0),
        FoodServingUnit("tbsp", "tablespoon", TBSP_ML),
        FoodServingUnit("tsp", "teaspoon", TSP_ML),
        FoodServingUnit("piece", "cup", 180.0),
        FoodServingUnit("grams", "grams", ML)
    )

    private fun flatbreadUnits(): List<FoodServingUnit> = listOf(
        FoodServingUnit("roti", "roti/chapati", 40.0, isDefault = true),
        FoodServingUnit("piece", "piece", 40.0),
        FoodServingUnit("serve", "serve", 80.0),
        FoodServingUnit("grams", "grams", 1.0),
        FoodServingUnit("oz", "oz", OZ_G)
    )

    private fun dosaIdliUnits(name: String): List<FoodServingUnit> {
        val isIdli = name.lowercase().contains("idli")
        val pieceG = if (isIdli) 38.0 else 120.0
        return listOf(
            FoodServingUnit("medium", "medium", pieceG, isDefault = true),
            FoodServingUnit("small", "small", pieceG * 0.75),
            FoodServingUnit("large", "large", pieceG * 1.35),
            FoodServingUnit("piece", "piece", pieceG),
            FoodServingUnit("serve", "serve", pieceG * 2),
            FoodServingUnit("grams", "grams", 1.0),
            FoodServingUnit("oz", "oz", OZ_G)
        )
    }

    private fun riceUnits(): List<FoodServingUnit> = listOf(
        FoodServingUnit("cup", "cup", 158.0, isDefault = true),
        FoodServingUnit("serve", "serve", 200.0),
        FoodServingUnit("bowl", "bowl", 250.0),
        FoodServingUnit("grams", "grams", 1.0),
        FoodServingUnit("oz", "oz", OZ_G)
    )

    private fun fruitUnits(name: String): List<FoodServingUnit> = listOf(
        FoodServingUnit("piece", "piece", 120.0, isDefault = true),
        FoodServingUnit("small", "small", 90.0),
        FoodServingUnit("medium", "medium", 120.0),
        FoodServingUnit("large", "large", 180.0),
        FoodServingUnit("cup", "cup", 150.0),
        FoodServingUnit("grams", "grams", 1.0),
        FoodServingUnit("oz", "oz", OZ_G)
    )

    private fun proteinUnits(): List<FoodServingUnit> = listOf(
        FoodServingUnit("serve", "serve", 100.0, isDefault = true),
        FoodServingUnit("piece", "piece", 50.0),
        FoodServingUnit("grams", "grams", 1.0),
        FoodServingUnit("oz", "oz", OZ_G)
    )

    private fun genericUnits(name: String): List<FoodServingUnit> {
        val short = name.split(" ").first().lowercase()
        return listOf(
            FoodServingUnit("serve", "serve", 100.0, isDefault = true),
            FoodServingUnit("piece", "piece", 50.0),
            FoodServingUnit("cup", "cup", 150.0),
            FoodServingUnit("grams", "grams", 1.0, isDefault = false),
            FoodServingUnit("oz", "oz", OZ_G)
        ).let { list ->
            if (short.isNotBlank()) {
                listOf(FoodServingUnit("item", short, 100.0, isDefault = true)) + list.filter { it.id != "serve" }
            } else list
        }
    }
}
