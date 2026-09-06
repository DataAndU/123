package com.prosperity.game.network.dto

data class Job(
    val id: String,
    val title: String,
    val tier: Int,
    val requiredEducation: String,
    val requiredSkill: String?,
    val requiredSkillLevel: Int,
    val baseSalary: Double,
    val skillGainPerMonth: Map<String, Double>,
    val happinessImpact: Double
)

data class EducationProgram(
    val id: String,
    val title: String,
    val grantsLevel: String,
    val tuitionCost: Double,
    val durationMonths: Int,
    val happinessImpactPerMonth: Double
)

val EDUCATION_ORDER = listOf("NONE", "HIGH_SCHOOL", "COLLEGE", "BACHELOR", "MASTER", "PHD")
val SKILL_TYPES = listOf("BUSINESS", "FINANCE", "TECH", "MARKETING", "LABOR")
val LIFESTYLE_TIERS = listOf("SPARTAN", "MODEST", "COMFORTABLE", "LUXURY", "ELITE")

data class BusinessTypeSpec(
    val id: String,
    val displayName: String,
    val startupCost: Double
)

/** Static mirror of the server's BUSINESS_CATALOG — only used to label the "start a business" picker. */
val BUSINESS_TYPE_CATALOG = listOf(
    BusinessTypeSpec("RESTAURANT", "Restaurant", 15_000.0),
    BusinessTypeSpec("GROCERY_STORE", "Grocery Store", 25_000.0),
    BusinessTypeSpec("TECH_COMPANY", "Technology Startup", 40_000.0),
    BusinessTypeSpec("MANUFACTURING", "Manufacturing Plant", 80_000.0),
    BusinessTypeSpec("TRANSPORT", "Transport & Logistics", 50_000.0),
    BusinessTypeSpec("CONSTRUCTION", "Construction Firm", 60_000.0),
    BusinessTypeSpec("ONLINE_BUSINESS", "Online Business", 8_000.0)
)
