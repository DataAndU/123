package com.prosperity.game.engine.business

import kotlinx.serialization.Serializable

@Serializable
enum class BusinessType {
    RESTAURANT, GROCERY_STORE, TECH_COMPANY, MANUFACTURING, TRANSPORT, CONSTRUCTION, ONLINE_BUSINESS
}

/** Static tuning numbers for each business type — the "rules" the sim plays by. */
data class BusinessTypeSpec(
    val displayName: String,
    val startupCost: Double,
    val baseRevenuePerEmployee: Double,
    val baseSalaryPerEmployee: Double,
    val baseRent: Double,
    val hasInventory: Boolean,
    /** How strongly this business's demand swings with the economy (0=defensive, 2=very cyclical). */
    val cyclicality: Double,
    val corporateTaxRate: Double = 0.20
)

object BusinessCatalog {
    val specs: Map<BusinessType, BusinessTypeSpec> = mapOf(
        BusinessType.RESTAURANT to BusinessTypeSpec("Restaurant", 15_000.0, 2800.0, 2200.0, 1800.0, hasInventory = true, cyclicality = 1.1),
        BusinessType.GROCERY_STORE to BusinessTypeSpec("Grocery Store", 25_000.0, 3200.0, 2000.0, 2200.0, hasInventory = true, cyclicality = 0.4),
        BusinessType.TECH_COMPANY to BusinessTypeSpec("Technology Startup", 40_000.0, 6500.0, 4800.0, 2500.0, hasInventory = false, cyclicality = 1.6),
        BusinessType.MANUFACTURING to BusinessTypeSpec("Manufacturing Plant", 80_000.0, 5200.0, 3200.0, 4000.0, hasInventory = true, cyclicality = 1.4),
        BusinessType.TRANSPORT to BusinessTypeSpec("Transport & Logistics", 50_000.0, 4200.0, 2800.0, 1500.0, hasInventory = false, cyclicality = 1.2),
        BusinessType.CONSTRUCTION to BusinessTypeSpec("Construction Firm", 60_000.0, 5800.0, 3400.0, 1200.0, hasInventory = true, cyclicality = 1.8),
        BusinessType.ONLINE_BUSINESS to BusinessTypeSpec("Online Business", 8_000.0, 3400.0, 2600.0, 400.0, hasInventory = false, cyclicality = 0.9)
    )
}

@Serializable
data class MonthlyFinancials(
    val month: Int,
    val revenue: Double,
    val expenses: Double,
    val profit: Double
)

@Serializable
data class Business(
    val id: String,
    val type: BusinessType,
    val name: String,
    val level: Int = 1,
    val employees: Int = 1,
    /** 0.5 = deep discount, 1.0 = market price, 2.0 = premium pricing. */
    val pricePointMultiplier: Double = 1.0,
    val reputation: Double = 55.0,
    val advertisingBudgetMonthly: Double = 0.0,
    val competitionPressure: Double = 10.0,
    val businessCash: Double = 0.0,
    val loanBalance: Double = 0.0,
    val loanInterestRate: Double = 0.0,
    val history: List<MonthlyFinancials> = emptyList()
) {
    val upgradeCost: Double get() = BusinessCatalog.specs.getValue(type).startupCost * 0.6 * level
    val maxLevel = 5
}
