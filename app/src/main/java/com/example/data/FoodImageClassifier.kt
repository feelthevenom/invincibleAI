package com.example.data

/** Heuristics for food image lookup (dish vs packaged product). */
object FoodImageClassifier {

    private val dishKeywords = setOf(
        "biryani", "biriyani", "pulao", "pulav", "curry", "gravy", "idli", "dosa", "vada",
        "sambar", "rasam", "paratha", "roti", "chapati", "naan", "khichdi", "upma", "poha",
        "mutton", "chicken", "paneer", "fish", "egg", "rice", "dal", "daal", "lentil",
        "sabzi", "sabji", "thali", "kebab", "tikka", "masala", "soup", "salad", "sandwich",
        "burger", "pizza", "pasta", "noodle", "stew", "roast", "grill", "steak", "shrimp",
        "prawn", "lobster", "tofu", "tempeh", "oatmeal", "porridge", "cereal", "smoothie",
        "shake", "juice", "lassi", "chutney", "pickle", "papad", "pakora", "bhaji", "fries"
    )

    private val genericImageTerms = setOf(
        "milk", "dairy", "logo", "icon", "placeholder", "default", "clipart", "svg",
        "wikimedia", "commons-logo", "example", "sample", "unknown", "no image"
    )

    fun isPreparedDish(name: String): Boolean {
        val lower = name.lowercase()
        return dishKeywords.any { lower.contains(it) }
    }

    fun expandNameVariants(name: String): List<String> {
        val base = name.trim()
        if (base.isBlank()) return emptyList()
        val variants = linkedSetOf(base)
        val lower = base.lowercase()
        if (lower.contains("biriyani")) {
            variants.add(base.replace("biriyani", "biryani", ignoreCase = true))
        }
        if (lower.contains("biryani")) {
            variants.add(base.replace("biryani", "biriyani", ignoreCase = true))
        }
        if (lower.contains("daal")) {
            variants.add(base.replace("daal", "dal", ignoreCase = true))
        }
        if (lower.contains(" dal ") || lower.endsWith(" dal")) {
            variants.add(base.replace("dal", "daal", ignoreCase = true))
        }
        return variants.filter { it.isNotBlank() }
    }

    fun isRejectedImageTitle(title: String, query: String): Boolean {
        val lower = title.lowercase()
        if (genericImageTerms.any { term -> lower.contains(term) && !query.lowercase().contains(term) }) {
            return true
        }
        if (lower.length < 4) return true
        return false
    }

    fun wikimediaTitleScore(query: String, title: String): Int {
        if (isRejectedImageTitle(title, query)) return 0
        val base = FuzzyMatcher.score(query, title)
        val dishBoost = if (isPreparedDish(query) && isPreparedDish(title)) 80 else 0
        return base + dishBoost
    }
}
