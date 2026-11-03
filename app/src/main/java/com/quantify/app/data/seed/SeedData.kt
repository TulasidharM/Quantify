package com.quantify.app.data.seed

import com.quantify.app.data.local.QuantifyDatabase
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedData @Inject constructor(
    private val db: QuantifyDatabase
) {

    private val defaultExpenseCategories = listOf(
        Triple("Food & Dining", 0xFFFF5722L, "restaurant"),
        Triple("Transport", 0xFF2196F3L, "directions_car"),
        Triple("Shopping", 0xFFE91E63L, "shopping_bag"),
        Triple("Bills & Utilities", 0xFFFF9800L, "receipt_long"),
        Triple("Health", 0xFFFF4444L, "favorite"),
        Triple("Entertainment", 0xFF9C27B0L, "theater_comedy"),
        Triple("Education", 0xFF3F51B5L, "school"),
        Triple("Travel", 0xFF00BCD4L, "flight"),
        Triple("Home", 0xFF795548L, "home"),
        Triple("Salary", 0xFF4CAF50L, "account_balance_wallet"),
        Triple("Gifts", 0xFFE040FBL, "card_giftcard"),
        Triple("Other", 0xFF607D8BL, "more_horiz")
    )

    private val defaultIncomeCategories = listOf(
        Triple("Salary", 0xFF4CAF50L, "account_balance_wallet"),
        Triple("Freelance", 0xFF2196F3L, "work"),
        Triple("Investments", 0xFFFF9800L, "trending_up"),
        Triple("Gifts", 0xFFE040FBL, "card_giftcard"),
        Triple("Refunds", 0xFF00BCD4L, "replay"),
        Triple("Other Income", 0xFF607D8BL, "more_horiz")
    )

    // Sub-categories: parent name -> list of (sub name, color, icon)
    private val defaultSubCategories = mapOf(
        "Food & Dining" to listOf(
            Triple("Groceries", 0xFFFF7043L, "shopping_cart"),
            Triple("Restaurants", 0xFFFF8A65L, "restaurant"),
            Triple("Dining Out", 0xFFFFAB91L, "lunch_dining"),
            Triple("Snacks & Drinks", 0xFFD84315L, "local_drink")
        ),
        "Transport" to listOf(
            Triple("Fuel", 0xFF1E88E5L, "local_gas_station"),
            Triple("Public Transit", 0xFF42A5F5L, "train"),
            Triple("Ride Sharing", 0xFF1976D2L, "local_taxi"),
            Triple("Parking", 0xFF90CAF9L, "local_parking")
        ),
        "Shopping" to listOf(
            Triple("Clothing", 0xFFE91E63L, "checkroom"),
            Triple("Electronics", 0xFFF06292L, "computer"),
            Triple("Home Supplies", 0xFFF48FB1L, "cleaning_services")
        ),
        "Entertainment" to listOf(
            Triple("Movies", 0xFF8E24AA, "movie"),
            Triple("Streaming", 0xFFAB47BC, "smart_display"),
            Triple("Gaming", 0xFF7B1FA2L, "sports_esports"),
            Triple("Music", 0xFFBA68C8L, "music_note")
        ),
        "Bills & Utilities" to listOf(
            Triple("Electricity", 0xFFFFA726L, "bolt"),
            Triple("Water", 0xFFFFB74DL, "water_drop"),
            Triple("Internet", 0xFFFF9DDAL, "wifi"),
            Triple("Phone Bill", 0xFFFF9800L, "smartphone")
        ),
        "Travel" to listOf(
            Triple("Flights", 0xFF00ACC1L, "flight"),
            Triple("Hotels", 0xFF26C6DAL, "hotel"),
            Triple("Local Transport", 0xFF4DD0E1L, "directions_car")
        ),
        "Salary" to listOf( // income sub-categories
            Triple("Base Salary", 0xFF43A047L, "account_balance_wallet"),
            Triple("Bonus", 0xFF66BB6AL, "emoji_events"),
            Triple("Overtime", 0xFF81C784L, "access_time")
        ),
        "Freelance" to listOf(
            Triple("Project Payment", 0xFF1E88E5L, "assignment"),
            Triple("Consulting", 0xFF42A5F5L, "business_center")
        )
    )

    private val defaultPaymentMethods = listOf("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer", "Wallet")

    suspend fun seedIfEmpty() {
        withContext(Dispatchers.IO) {
            val existing = db.categoryDao().getAll().first()
            if (existing.isEmpty()) {
                // Seed expense categories
                val categoryIds = mutableMapOf<String, String>()
                defaultExpenseCategories.forEach { (name, color, icon) ->
                    val id = name.lowercase().replace(" ", "_")
                    categoryIds[name] = id
                    db.categoryDao().insert(CategoryEntity(
                        id = id,
                        name = name,
                        type = "EXPENSE",
                        color = color,
                        icon = icon,
                        isDefault = name == "Other"
                    ))
                }
                // Seed income categories
                defaultIncomeCategories.forEach { (name, color, icon) ->
                    val id = "inc_${name.lowercase().replace(" ", "_")}"
                    categoryIds[name] = id
                    db.categoryDao().insert(CategoryEntity(
                        id = id,
                        name = name,
                        type = "INCOME",
                        color = color,
                        icon = icon,
                        isDefault = name == "Other Income"
                    ))
                }
                // Seed sub-categories
                defaultSubCategories.forEach { (parentName, subs) ->
                    val parentId = categoryIds[parentName]
                    if (parentId != null) {
                        subs.forEach { (subName, color, icon) ->
                            val subId = parentId + "_" + subName.lowercase().replace(" ", "_")
                            val subType = if (parentId.startsWith("inc_")) "INCOME" else "EXPENSE"
                            db.categoryDao().insert(CategoryEntity(
                                id = subId,
                                name = subName,
                                type = subType,
                                color = color,
                                icon = icon,
                                isDefault = false,
                                parentId = parentId
                            ))
                        }
                    }
                }
                // Seed payment methods
                defaultPaymentMethods.forEachIndexed { index, name ->
                    db.paymentMethodDao().insert(PaymentMethodEntity(
                        id = name.lowercase().replace(" ", "_"),
                        name = name,
                        isDefault = index == 0
                    ))
                }
            }
        }
    }
}
