package com.prosperity.game.engine.player

import kotlinx.serialization.Serializable

@Serializable
enum class SkillType { BUSINESS, FINANCE, TECH, MARKETING, LABOR }

@Serializable
enum class EducationLevel(val displayName: String) {
    NONE("No formal education"),
    HIGH_SCHOOL("High School Diploma"),
    COLLEGE("College Certificate"),
    BACHELOR("Bachelor's Degree"),
    MASTER("Master's Degree"),
    PHD("Ph.D.")
}

@Serializable
enum class LifestyleTier(val displayName: String, val monthlyCost: Double, val happinessBonus: Double) {
    SPARTAN("Spartan", 600.0, -8.0),
    MODEST("Modest", 1400.0, 0.0),
    COMFORTABLE("Comfortable", 2600.0, 8.0),
    LUXURY("Luxury", 5000.0, 16.0),
    ELITE("Elite", 10_000.0, 24.0)
}

data class Job(
    val id: String,
    val title: String,
    val tier: Int,
    val requiredEducation: EducationLevel,
    val requiredSkill: SkillType?,
    val requiredSkillLevel: Int,
    val baseSalary: Double,
    val skillGainPerMonth: Map<SkillType, Int>,
    val happinessImpact: Double
)

object JobCatalog {
    val jobs: List<Job> = listOf(
        Job("JOB_CASHIER", "Retail Cashier", 1, EducationLevel.NONE, null, 0, 1800.0, mapOf(SkillType.LABOR to 1), -2.0),
        Job("JOB_DRIVER", "Delivery Driver", 1, EducationLevel.NONE, SkillType.LABOR, 5, 2200.0, mapOf(SkillType.LABOR to 2), -3.0),
        Job("JOB_CLERK", "Office Clerk", 2, EducationLevel.HIGH_SCHOOL, null, 0, 2600.0, mapOf(SkillType.BUSINESS to 1), 0.0),
        Job("JOB_SALES", "Sales Representative", 2, EducationLevel.HIGH_SCHOOL, SkillType.MARKETING, 10, 3200.0, mapOf(SkillType.MARKETING to 2), 1.0),
        Job("JOB_TECH_SUPPORT", "IT Support Technician", 2, EducationLevel.COLLEGE, SkillType.TECH, 10, 3600.0, mapOf(SkillType.TECH to 2), 1.0),
        Job("JOB_ACCOUNTANT", "Junior Accountant", 3, EducationLevel.BACHELOR, SkillType.FINANCE, 15, 4800.0, mapOf(SkillType.FINANCE to 2), 3.0),
        Job("JOB_SW_ENGINEER", "Software Engineer", 3, EducationLevel.BACHELOR, SkillType.TECH, 25, 6200.0, mapOf(SkillType.TECH to 3), 4.0),
        Job("JOB_MARKETING_MGR", "Marketing Manager", 4, EducationLevel.BACHELOR, SkillType.MARKETING, 35, 7000.0, mapOf(SkillType.MARKETING to 3), 5.0),
        Job("JOB_FINANCE_MGR", "Finance Manager", 4, EducationLevel.MASTER, SkillType.FINANCE, 40, 9000.0, mapOf(SkillType.FINANCE to 3), 6.0),
        Job("JOB_SENIOR_ENGINEER", "Senior Software Architect", 5, EducationLevel.MASTER, SkillType.TECH, 55, 12_000.0, mapOf(SkillType.TECH to 3), 7.0),
        Job("JOB_EXECUTIVE", "Chief Executive Officer", 6, EducationLevel.PHD, SkillType.BUSINESS, 70, 20_000.0, mapOf(SkillType.BUSINESS to 4), 10.0)
    )

    fun byId(id: String?): Job? = jobs.firstOrNull { it.id == id }

    fun isEligible(job: Job, education: EducationLevel, skills: Map<SkillType, Int>): Boolean {
        if (education.ordinal < job.requiredEducation.ordinal) return false
        val skill = job.requiredSkill ?: return true
        return (skills[skill] ?: 0) >= job.requiredSkillLevel
    }
}

data class EducationProgram(
    val id: String,
    val title: String,
    val grantsLevel: EducationLevel,
    val tuitionCost: Double,
    val durationMonths: Int,
    val happinessImpactPerMonth: Double
)

object EducationCatalog {
    val programs: List<EducationProgram> = listOf(
        EducationProgram("EDU_HS", "Adult High School Program", EducationLevel.HIGH_SCHOOL, 500.0, 4, -1.0),
        EducationProgram("EDU_COLLEGE", "Community College Certificate", EducationLevel.COLLEGE, 3000.0, 8, -1.5),
        EducationProgram("EDU_BACHELOR", "Bachelor's Degree Program", EducationLevel.BACHELOR, 12_000.0, 24, -2.0),
        EducationProgram("EDU_MASTER", "Master's Degree Program", EducationLevel.MASTER, 20_000.0, 18, -2.5),
        EducationProgram("EDU_PHD", "Ph.D. Program", EducationLevel.PHD, 35_000.0, 30, -3.0)
    )

    fun byId(id: String?): EducationProgram? = programs.firstOrNull { it.id == id }
}

@Serializable
enum class LoanType { PERSONAL, MORTGAGE, STUDENT }

@Serializable
data class Loan(
    val id: String,
    val type: LoanType,
    val principalRemaining: Double,
    val annualRate: Double,
    val monthlyPayment: Double,
    val originalPrincipal: Double,
    val termMonthsRemaining: Int
)

@Serializable
data class PropertyHolding(
    val id: String,
    val purchasePrice: Double,
    val purchaseHousingIndex: Double,
    val mortgageBalance: Double,
    val mortgageRate: Double,
    val monthlyRentIncome: Double
)

@Serializable
data class PlayerState(
    val cash: Double = 2500.0,
    val bankSavings: Double = 0.0,
    val currentJobId: String? = null,
    val jobMonthsHeld: Int = 0,
    val educationLevel: EducationLevel = EducationLevel.HIGH_SCHOOL,
    val educationInProgressId: String? = null,
    val educationMonthsRemaining: Int = 0,
    val skills: Map<String, Int> = SkillType.entries.associate { it.name to 5 },
    val loans: List<Loan> = emptyList(),
    val lifestyleTier: LifestyleTier = LifestyleTier.SPARTAN,
    val happiness: Double = 65.0,
    val health: Double = 80.0,
    val reputation: Double = 50.0,
    val stockHoldings: Map<String, Int> = emptyMap(),
    val bondHoldings: Map<String, Int> = emptyMap(),
    val commodityHoldings: Map<String, Double> = emptyMap(),
    val properties: List<PropertyHolding> = emptyList(),
    val foreignCurrencyHoldings: Double = 0.0,
    val businesses: List<com.prosperity.game.engine.business.Business> = emptyList(),
    val achievementsUnlocked: Set<String> = emptySet(),
    val monthsSinceNegativeCash: Int = 0,
    val cashHistory: List<Double> = listOf(cash),
    val incomeHistory: List<Double> = emptyList(),
    val expenseHistory: List<Double> = emptyList(),
    val netWorthHistory: List<Double> = emptyList()
) {
    fun skill(type: SkillType): Int = skills[type.name] ?: 0
}
