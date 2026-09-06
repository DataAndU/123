package com.prosperity.game.engine.market

/** Builds the starting universe of tradable instruments for a new game. */
object MarketCatalog {

    fun initialState(baseInterestRate: Double = 3.0): MarketState = MarketState(
        stocks = listOf(
            Stock("STK_TCH", "Nimbus Cloud Systems", Sector.TECH, price = 42.0, beta = 1.5, volatility = 0.09, dividendYieldAnnual = 0.0),
            Stock("STK_NRG", "Continental Energy Co.", Sector.ENERGY, price = 65.0, beta = 0.8, volatility = 0.07, dividendYieldAnnual = 4.5),
            Stock("STK_CNS", "Harbor Retail Group", Sector.CONSUMER, price = 28.0, beta = 0.7, volatility = 0.05, dividendYieldAnnual = 2.0),
            Stock("STK_FIN", "Meridian Bank Holdings", Sector.FINANCE, price = 55.0, beta = 1.2, volatility = 0.08, dividendYieldAnnual = 3.2),
            Stock("STK_IND", "Ironclad Industrial", Sector.INDUSTRIAL, price = 37.0, beta = 1.1, volatility = 0.07, dividendYieldAnnual = 2.5),
            Stock("STK_HLT", "Willowbrook Health", Sector.HEALTHCARE, price = 80.0, beta = 0.5, volatility = 0.04, dividendYieldAnnual = 1.8)
        ),
        bonds = listOf(
            Bond("BND_GOV2", "2-Year Treasury Note", BondIssuer.GOVERNMENT, faceValue = 1000.0, couponRate = baseInterestRate, originalTermMonths = 24, monthsRemaining = 24, price = 1000.0, riskPremium = 0.0),
            Bond("BND_GOV10", "10-Year Treasury Bond", BondIssuer.GOVERNMENT, faceValue = 1000.0, couponRate = baseInterestRate + 0.6, originalTermMonths = 120, monthsRemaining = 120, price = 1000.0, riskPremium = 0.6),
            Bond("BND_CORP", "Meridian Corporate Bond", BondIssuer.CORPORATE, faceValue = 1000.0, couponRate = baseInterestRate + 2.5, originalTermMonths = 60, monthsRemaining = 60, price = 1000.0, riskPremium = 2.5)
        ),
        commodities = listOf(
            Commodity("COM_GOLD", "Gold (oz)", price = 1900.0, safeHavenFactor = 1.0, volatility = 0.03),
            Commodity("COM_OIL", "Crude Oil (barrel)", price = 75.0, safeHavenFactor = -0.4, volatility = 0.08)
        ),
        housingPriceIndex = 100.0,
        exchangeRate = 1.0
    )
}
