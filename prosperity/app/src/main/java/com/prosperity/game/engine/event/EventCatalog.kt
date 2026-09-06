package com.prosperity.game.engine.event

import com.prosperity.game.engine.business.BusinessCatalog
import com.prosperity.game.engine.business.BusinessSimulator
import com.prosperity.game.engine.economy.BusinessCyclePhase
import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.player.JobCatalog
import kotlin.random.Random

private fun EventContext.mapEconomy(f: (EconomyState) -> EconomyState) = copy(economy = f(economy))
private fun EventContext.mapPlayer(f: (com.prosperity.game.engine.player.PlayerState) -> com.prosperity.game.engine.player.PlayerState) = copy(player = f(player))
private fun EventContext.mapMarkets(f: (com.prosperity.game.engine.market.MarketState) -> com.prosperity.game.engine.market.MarketState) = copy(markets = f(markets))
private val noOp: (EventContext, Random) -> EventContext = { ctx, _ -> ctx }

/**
 * ~28 hand-written events across every requested category. Each carries a
 * short, plain-English educational note and at least two real choices —
 * "do nothing" is always an option, but it always has an opportunity cost.
 */
object EventCatalog {

    val all: List<EventDefinition> = listOf(

        // ---------------- ECONOMY ----------------
        EventDefinition(
            id = "recession_warning", category = EventCategory.ECONOMY,
            title = "Economists Warn of a Coming Slowdown",
            description = "Several leading indicators are flashing yellow. A recession may be on the horizon, though nobody can say exactly when.",
            educationalNote = "A business cycle naturally alternates between expansion and recession. Leading indicators (like slowing confidence or an inverted yield curve) can hint at a slowdown, but timing it precisely is hard even for professionals — this is why diversification matters more than prediction.",
            weight = { e -> if (e.phase == BusinessCyclePhase.PEAK) 3.5 else if (e.phase == BusinessCyclePhase.EXPANSION && e.phaseMonthsElapsed > 15) 2.0 else 0.3 },
            options = listOf(
                EventOption("prepare", "Tighten budgets now", "You trimmed advertising spend across your businesses as a precaution.") { ctx, _ ->
                    ctx.mapPlayer { p -> p.copy(businesses = p.businesses.map { it.copy(advertisingBudgetMonthly = it.advertisingBudgetMonthly * 0.7) }) }
                },
                EventOption("ignore", "Continue as usual", "You decided the warning was too uncertain to act on.", noOp)
            )
        ),
        EventDefinition(
            id = "rate_hike", category = EventCategory.ECONOMY,
            title = "Central Bank Raises Interest Rates",
            description = "To fight rising prices, the central bank has raised its policy rate by a full percentage point.",
            educationalNote = "Interest rates increased. This makes borrowing more expensive, which can reduce business investment and consumer spending — the central bank's main tool for cooling an overheating, inflationary economy.",
            weight = { e -> if (e.inflationRate > 4.0) 3.0 else 0.5 },
            options = listOf(
                EventOption("refinance", "Refinance loans to lock in a rate", "You paid a small fee to lock your loans at a lower fixed rate before it climbs further.") { ctx, _ ->
                    val bumped = ctx.mapEconomy { it.copy(interestRate = (it.interestRate + 1.0).coerceAtMost(30.0)) }
                    val fee = bumped.player.loans.sumOf { it.principalRemaining } * 0.01
                    if (fee > bumped.player.cash || bumped.player.loans.isEmpty()) bumped
                    else bumped.mapPlayer { p ->
                        p.copy(cash = p.cash - fee, loans = p.loans.map { it.copy(annualRate = (it.annualRate - 1.5).coerceAtLeast(1.0)) })
                    }
                },
                EventOption("nothing", "Do nothing", "You left your existing loans as they were.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(interestRate = (it.interestRate + 1.0).coerceAtMost(30.0)) }
                }
            )
        ),
        EventDefinition(
            id = "rate_cut", category = EventCategory.ECONOMY,
            title = "Central Bank Cuts Interest Rates",
            description = "With unemployment rising, the central bank has cut its policy rate by a full percentage point to stimulate the economy.",
            educationalNote = "Lower interest rates make borrowing cheaper, encouraging businesses to invest and consumers to spend — this is expansionary monetary policy, typically used to fight recessions.",
            weight = { e -> if (e.unemploymentRate > 7.0 || e.phase == BusinessCyclePhase.RECESSION) 3.0 else 0.4 },
            options = listOf(
                EventOption("borrow", "Take advantage with a cheap business loan", "You borrowed while rates were low to expand a business.") { ctx, rng ->
                    val target = ctx.player.businesses.randomOrNull(rng) ?: return@EventOption ctx
                    ctx.mapPlayer { p ->
                        p.copy(businesses = p.businesses.map {
                            if (it.id == target.id) BusinessSimulator.takeLoan(it, BusinessCatalog.specs.getValue(it.type).startupCost * 0.3, ctx.economy.interestRate + 2.0)
                            else it
                        })
                    }
                }.let { it.copy(apply = { ctx, rng -> it.apply(ctx.mapEconomy { e -> e.copy(interestRate = (e.interestRate - 1.0).coerceAtLeast(0.0)) }, rng) }) },
                EventOption("nothing", "Do nothing", "You kept your finances unchanged.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(interestRate = (it.interestRate - 1.0).coerceAtLeast(0.0)) }
                }
            )
        ),
        EventDefinition(
            id = "inflation_spike", category = EventCategory.ECONOMY,
            title = "Inflation Jumps Sharply",
            description = "Prices across the economy are rising faster than expected, eroding the purchasing power of cash and fixed incomes.",
            educationalNote = "Inflation is a general rise in prices that reduces how much your money can buy. Cash sitting idle loses real value during high inflation — assets like stocks, real estate, or commodities have historically been better long-term hedges.",
            weight = { e -> if (e.inflationRate < 5.0) 1.2 else 0.3 },
            options = listOf(
                EventOption("hedge", "Move some cash into gold", "You bought gold as an inflation hedge.") { ctx, _ ->
                    val amount = (ctx.player.cash * 0.15)
                    val gold = ctx.markets.commodity("COM_GOLD")
                    if (amount < gold.price) ctx
                    else ctx.mapPlayer { p -> p.copy(cash = p.cash - amount, commodityHoldings = p.commodityHoldings + ("COM_GOLD" to (p.commodityHoldings["COM_GOLD"] ?: 0.0) + amount / gold.price)) }
                }.let { opt -> opt.copy(apply = { c, r -> opt.apply(c, r).mapEconomy { it.copy(inflationRate = it.inflationRate + 1.5) } }) },
                EventOption("nothing", "Hold cash", "You kept your cash as-is.") { ctx, _ -> ctx.mapEconomy { it.copy(inflationRate = it.inflationRate + 1.5) } }
            )
        ),
        EventDefinition(
            id = "government_stimulus", category = EventCategory.ECONOMY,
            title = "Government Sends Stimulus Payments",
            description = "To support the economy, the government is sending a direct payment to every citizen.",
            educationalNote = "This is expansionary fiscal policy: the government spends money (or cuts taxes) directly to boost demand during a downturn. It can help a struggling economy, but funding it usually means more government borrowing or, eventually, higher taxes or inflation.",
            weight = { e -> if (e.phase == BusinessCyclePhase.RECESSION || e.phase == BusinessCyclePhase.TROUGH) 2.5 else 0.2 },
            options = listOf(
                EventOption("save", "Save the payment", "You deposited the stimulus into savings.") { ctx, _ ->
                    ctx.mapPlayer { it.copy(bankSavings = it.bankSavings + 1200.0) }
                },
                EventOption("spend", "Spend it now", "You enjoyed the extra cash right away.") { ctx, _ ->
                    ctx.mapPlayer { it.copy(cash = it.cash + 1200.0, happiness = (it.happiness + 5.0).coerceAtMost(100.0)) }
                }
            )
        ),
        EventDefinition(
            id = "tax_increase", category = EventCategory.ECONOMY,
            title = "Government Raises Taxes",
            description = "Facing a budget shortfall, the government has raised both personal income tax and corporate tax rates.",
            educationalNote = "Taxation funds government spending (roads, healthcare, defense) but reduces take-home income and business profit. Policymakers constantly balance tax revenue against the drag higher taxes can put on growth.",
            weight = { e -> if (e.phase == BusinessCyclePhase.EXPANSION || e.phase == BusinessCyclePhase.PEAK) 1.5 else 0.3 },
            options = listOf(
                EventOption("accept", "Accept the new rates", "The new tax rates took effect.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(incomeTaxRate = it.incomeTaxRate + 3.0, corporateTaxSurcharge = it.corporateTaxSurcharge + 3.0) }
                }
            )
        ),
        EventDefinition(
            id = "tax_cut", category = EventCategory.ECONOMY,
            title = "Government Cuts Taxes",
            description = "To encourage growth, the government has cut both personal income tax and corporate tax rates.",
            educationalNote = "Tax cuts leave people and businesses with more after-tax money, which can boost spending and investment — but they also reduce government revenue, which may need to be offset by borrowing or future spending cuts.",
            weight = { e -> if (e.phase == BusinessCyclePhase.RECESSION) 1.8 else 0.3 },
            options = listOf(
                EventOption("accept", "Accept the new rates", "The new tax rates took effect.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(incomeTaxRate = (it.incomeTaxRate - 3.0).coerceAtLeast(0.0), corporateTaxSurcharge = (it.corporateTaxSurcharge - 3.0).coerceAtLeast(-10.0)) }
                }
            )
        ),
        EventDefinition(
            id = "unemployment_report", category = EventCategory.ECONOMY,
            title = "Unemployment Report Released",
            description = "The latest jobs report shows the labor market shifting. Training programs are being subsidized to help workers adapt.",
            educationalNote = "Unemployment and growth are closely linked (Okun's Law): when growth slows, job losses tend to follow. Investing in your own skills is one of the few hedges an individual has against a weak job market.",
            weight = { 1.0 },
            options = listOf(
                EventOption("train", "Take a subsidized training course", "You picked up a new skill at a discount.") { ctx, rng ->
                    val skill = com.prosperity.game.engine.player.SkillType.entries.toTypedArray().random(rng)
                    ctx.mapPlayer { p ->
                        val skills = p.skills.toMutableMap()
                        skills[skill.name] = ((skills[skill.name] ?: 0) + 4).coerceAtMost(100)
                        p.copy(skills = skills)
                    }
                },
                EventOption("skip", "Skip it", "You didn't have time for training right now.", noOp)
            )
        ),

        // ---------------- MARKET ----------------
        EventDefinition(
            id = "stock_market_crash", category = EventCategory.MARKET,
            title = "Stock Market Crash",
            description = "A wave of panic selling has wiped out a large chunk of stock market value in days.",
            educationalNote = "Markets can fall sharply and quickly on fear alone, even without new information about the underlying businesses. Historically, panic-selling near the bottom locks in losses — investors who hold quality assets through volatility have generally recovered over the long run, though every crash is different.",
            weight = { e -> if (e.phase == BusinessCyclePhase.RECESSION) 1.8 else 0.25 },
            options = listOf(
                EventOption("hold", "Hold your positions", "You rode out the volatility.", noOp),
                EventOption("sell", "Sell everything now", "You locked in your losses and moved to cash.") { ctx, _ ->
                    var cash = ctx.player.cash
                    ctx.markets.stocks.forEach { s -> cash += (ctx.player.stockHoldings[s.id] ?: 0) * s.price }
                    ctx.mapPlayer { it.copy(cash = cash, stockHoldings = emptyMap()) }
                },
                EventOption("buy", "Buy more at low prices", "You bought the dip, spending 20% of your cash.") { ctx, rng ->
                    val budget = ctx.player.cash * 0.2
                    val stock = ctx.markets.stocks.random(rng)
                    val shares = (budget / stock.price).toInt()
                    if (shares <= 0) ctx else ctx.mapPlayer { p ->
                        p.copy(cash = p.cash - shares * stock.price, stockHoldings = p.stockHoldings + (stock.id to (p.stockHoldings[stock.id] ?: 0) + shares))
                    }
                }
            )
        ),
        EventDefinition(
            id = "stock_market_rally", category = EventCategory.MARKET,
            title = "Stock Market Rallies",
            description = "Optimism is running high and stock prices have surged across the board.",
            educationalNote = "Strong rallies often follow periods of good economic news or improving confidence. Chasing a rally after it has already happened carries its own risk: you may be buying at a temporary peak.",
            weight = { e -> if (e.phase == BusinessCyclePhase.EXPANSION) 1.5 else 0.3 },
            options = listOf(
                EventOption("hold", "Stay the course", "You didn't change your holdings.", noOp),
                EventOption("take_profit", "Take some profit off the table", "You sold a third of each stock position to lock in gains.") { ctx, _ ->
                    var cash = ctx.player.cash
                    val newHoldings = ctx.player.stockHoldings.mapValues { (id, shares) ->
                        val toSell = shares / 3
                        cash += toSell * ctx.markets.stock(id).price
                        shares - toSell
                    }.filterValues { it > 0 }
                    ctx.mapPlayer { it.copy(cash = cash, stockHoldings = newHoldings) }
                }
            )
        ),
        EventDefinition(
            id = "housing_boom", category = EventCategory.MARKET,
            title = "Housing Prices Surge",
            description = "A wave of demand and cheap credit has sent housing prices sharply higher.",
            educationalNote = "Housing booms are often fueled by low interest rates and easy credit. Rapid price growth can turn into a bubble if prices detach from what buyers can actually afford to pay from their incomes.",
            weight = { e -> if (e.interestRate < 3.0) 2.0 else 0.4 },
            options = listOf(
                EventOption("sell", "Sell a property now", "You cashed out one property at the peak.") { ctx, _ ->
                    val property = ctx.player.properties.firstOrNull() ?: return@EventOption ctx
                    val value = property.purchasePrice * (ctx.markets.housingPriceIndex * 1.15 / property.purchaseHousingIndex) - property.mortgageBalance
                    ctx.mapPlayer { it.copy(cash = it.cash + value.coerceAtLeast(0.0), properties = it.properties.filterNot { p -> p.id == property.id }) }
                },
                EventOption("hold", "Hold for further gains", "You held onto your properties.", noOp)
            )
        ),
        EventDefinition(
            id = "housing_crash", category = EventCategory.MARKET,
            title = "Housing Market Crashes",
            description = "Housing prices have fallen sharply as credit tightens and buyers disappear.",
            educationalNote = "When housing prices fall below what owners paid — especially if they used a lot of mortgage debt — it's called being 'underwater.' This is why lenders and regulators watch loan-to-value ratios closely.",
            weight = { e -> if (e.interestRate > 6.0) 2.0 else 0.3 },
            options = listOf(
                EventOption("hold", "Hold your properties", "You decided to ride out the downturn.", noOp),
                EventOption("buy", "Buy a discounted property", "You picked up a property at a discount.") { ctx, _ ->
                    com.prosperity.game.engine.player.PlayerActions.buyProperty(ctx.player, ctx.markets, 0.25, ctx.economy.interestRate + 2.0).let {
                        if (it is com.prosperity.game.engine.player.ActionResult.Success) ctx.copy(player = it.player) else ctx
                    }
                }
            )
        ),
        EventDefinition(
            id = "oil_price_shock", category = EventCategory.MARKET,
            title = "Oil Prices Spike",
            description = "Geopolitical tension has sent oil prices sharply higher, raising costs across the economy.",
            educationalNote = "Oil is a key input for transport, manufacturing, and plastics. A sudden oil price shock raises production costs everywhere it's used, which is one classic cause of cost-push inflation.",
            weight = { 0.9 },
            options = listOf(
                EventOption("absorb", "Absorb the cost", "Your transport and manufacturing businesses took a temporary hit.") { ctx, _ ->
                    ctx.mapPlayer { p ->
                        p.copy(businesses = p.businesses.map {
                            if (it.type == com.prosperity.game.engine.business.BusinessType.TRANSPORT || it.type == com.prosperity.game.engine.business.BusinessType.MANUFACTURING)
                                it.copy(competitionPressure = (it.competitionPressure + 5.0).coerceAtMost(100.0))
                            else it
                        })
                    }.mapEconomy { it.copy(inflationRate = it.inflationRate + 0.8) }
                        .mapMarkets { m -> m.copy(commodities = m.commodities.map { if (it.id == "COM_OIL") it.copy(price = it.price * 1.25) else it }) }
                },
                EventOption("hedge", "Hedge by buying oil futures", "You bought oil, which rose in value with the shock.") { ctx, _ ->
                    val oil = ctx.markets.commodity("COM_OIL")
                    val budget = (ctx.player.cash * 0.1)
                    val units = budget / oil.price
                    ctx.mapPlayer { p -> p.copy(cash = p.cash - budget, commodityHoldings = p.commodityHoldings + ("COM_OIL" to (p.commodityHoldings["COM_OIL"] ?: 0.0) + units)) }
                        .mapEconomy { it.copy(inflationRate = it.inflationRate + 0.8) }
                        .mapMarkets { m -> m.copy(commodities = m.commodities.map { if (it.id == "COM_OIL") it.copy(price = it.price * 1.25) else it }) }
                }
            )
        ),
        EventDefinition(
            id = "commodity_shortage", category = EventCategory.MARKET,
            title = "Global Supply Shortage",
            description = "A shortage of key raw materials is squeezing businesses that rely on physical inventory.",
            educationalNote = "When supply falls but demand stays the same, prices rise — basic supply and demand. Businesses that hold inventory or depend on imported materials feel this fastest.",
            weight = { 0.8 },
            options = listOf(
                EventOption("raise_prices", "Raise your prices to compensate", "You raised prices across your inventory-based businesses.") { ctx, _ ->
                    ctx.mapPlayer { p -> p.copy(businesses = p.businesses.map {
                        if (BusinessCatalog.specs.getValue(it.type).hasInventory) it.copy(pricePointMultiplier = (it.pricePointMultiplier + 0.15).coerceAtMost(2.0))
                        else it
                    }) }
                },
                EventOption("absorb", "Absorb the cost to keep customers", "You kept prices steady, accepting thinner margins.", noOp)
            )
        ),

        // ---------------- BUSINESS ----------------
        EventDefinition(
            id = "new_competitor", category = EventCategory.BUSINESS,
            title = "A New Competitor Opens Nearby",
            description = "A well-funded competitor has opened up, targeting one of your businesses' customers.",
            educationalNote = "In a competitive market, new entrants are drawn in by visible profits. Businesses defend market share through better pricing, quality (reputation), or marketing — this is the essence of market competition.",
            weight = { 1.2 },
            options = listOf(
                EventOption("advertise", "Launch a marketing campaign", "You spent on advertising to defend your market share.") { ctx, rng ->
                    val target = ctx.player.businesses.randomOrNull(rng) ?: return@EventOption ctx
                    val cost = 2000.0
                    if (cost > ctx.player.cash) ctx
                    else ctx.mapPlayer { p ->
                        p.copy(cash = p.cash - cost, businesses = p.businesses.map { if (it.id == target.id) it.copy(advertisingBudgetMonthly = it.advertisingBudgetMonthly + 1500.0, competitionPressure = (it.competitionPressure - 8.0).coerceAtLeast(0.0)) else it })
                    }
                },
                EventOption("ignore", "Ignore it for now", "You decided not to react immediately.") { ctx, rng ->
                    val target = ctx.player.businesses.randomOrNull(rng) ?: return@EventOption ctx
                    ctx.mapPlayer { p -> p.copy(businesses = p.businesses.map { if (it.id == target.id) it.copy(competitionPressure = (it.competitionPressure + 10.0).coerceAtMost(100.0)) else it }) }
                }
            )
        ),
        EventDefinition(
            id = "technology_breakthrough", category = EventCategory.BUSINESS,
            title = "A Technology Breakthrough Hits the Market",
            description = "A new technology is reshaping how businesses operate, and tech stocks are surging.",
            educationalNote = "Technological progress can boost productivity economy-wide, but it also creates 'creative destruction' — new methods replace old ones, rewarding businesses and workers who adapt quickly.",
            weight = { e -> if (e.phase == BusinessCyclePhase.EXPANSION) 1.3 else 0.5 },
            options = listOf(
                EventOption("invest", "Invest in tech stocks", "You bought shares in the leading tech company.") { ctx, _ ->
                    val stock = ctx.markets.stock("STK_TCH")
                    val budget = ctx.player.cash * 0.15
                    val shares = (budget / stock.price).toInt()
                    if (shares <= 0) ctx else ctx.mapPlayer { p -> p.copy(cash = p.cash - shares * stock.price, stockHoldings = p.stockHoldings + (stock.id to (p.stockHoldings[stock.id] ?: 0) + shares)) }
                }.let { opt -> opt.copy(apply = { c, r -> opt.apply(c, r).mapMarkets { m -> m.copy(stocks = m.stocks.map { if (it.id == "STK_TCH") it.copy(price = it.price * 1.12) else it }) } }) },
                EventOption("upskill", "Upskill in technology instead", "You trained your tech skills to ride the wave.") { ctx, _ ->
                    ctx.mapPlayer { p ->
                        val skills = p.skills.toMutableMap()
                        skills[com.prosperity.game.engine.player.SkillType.TECH.name] = ((skills[com.prosperity.game.engine.player.SkillType.TECH.name] ?: 0) + 6).coerceAtMost(100)
                        p.copy(skills = skills)
                    }.mapMarkets { m -> m.copy(stocks = m.stocks.map { if (it.id == "STK_TCH") it.copy(price = it.price * 1.12) else it }) }
                }
            )
        ),
        EventDefinition(
            id = "supply_shortage_business", category = EventCategory.BUSINESS,
            title = "Your Suppliers Are Running Short",
            description = "One of your businesses is struggling to get the inventory it needs.",
            educationalNote = "Supply chains connect many businesses together — a shortage upstream can ripple into higher costs or lost sales downstream, even for a healthy, well-run business.",
            weight = { e -> 0.9 },
            options = listOf(
                EventOption("pay_premium", "Pay a premium for priority supply", "You paid extra to keep the shelves stocked.") { ctx, rng ->
                    val target = ctx.player.businesses.filter { BusinessCatalog.specs.getValue(it.type).hasInventory }.randomOrNull(rng) ?: return@EventOption ctx
                    val cost = 1500.0
                    if (cost > ctx.player.cash) ctx else ctx.mapPlayer { p -> p.copy(cash = p.cash - cost) }
                },
                EventOption("wait", "Wait it out", "You accepted a temporary dip in reputation from empty shelves.") { ctx, rng ->
                    val target = ctx.player.businesses.filter { BusinessCatalog.specs.getValue(it.type).hasInventory }.randomOrNull(rng) ?: return@EventOption ctx
                    ctx.mapPlayer { p -> p.copy(businesses = p.businesses.map { if (it.id == target.id) it.copy(reputation = (it.reputation - 5.0).coerceAtLeast(0.0)) else it }) }
                }
            )
        ),
        EventDefinition(
            id = "business_opportunity", category = EventCategory.OPPORTUNITY,
            title = "A Discounted Franchise Opportunity",
            description = "A local franchise is available at a steep discount if you can pay cash today.",
            educationalNote = "Opportunity cost means every choice has a trade-off: money spent grabbing this deal is money not available for something else. Good investors compare the expected return of an opportunity against what else they could do with the same capital.",
            weight = { 1.0 },
            options = listOf(
                EventOption("buy", "Buy the discounted business", "You started a new business at 40% off its usual cost.") { ctx, rng ->
                    val type = com.prosperity.game.engine.business.BusinessType.entries.toTypedArray().random(rng)
                    val cost = BusinessCatalog.specs.getValue(type).startupCost * 0.6
                    if (cost > ctx.player.cash) ctx
                    else ctx.mapPlayer { p ->
                        val biz = BusinessSimulator.startNew(type, "${BusinessCatalog.specs.getValue(type).displayName} #${p.businesses.size + 1}", "${ctx.economy.month}")
                        p.copy(cash = p.cash - cost, businesses = p.businesses + biz)
                    }
                },
                EventOption("skip", "Pass on it", "You decided the timing wasn't right.", noOp)
            )
        ),
        EventDefinition(
            id = "equipment_breakdown", category = EventCategory.BUSINESS,
            title = "Critical Equipment Breaks Down",
            description = "Key equipment at one of your businesses has failed unexpectedly.",
            educationalNote = "Unplanned costs are a normal part of running a business — this is why keeping a cash reserve (working capital) matters as much as chasing revenue growth.",
            weight = { e -> 0.7 },
            options = listOf(
                EventOption("repair", "Pay for a full repair", "You paid to fix it properly.") { ctx, rng ->
                    val target = ctx.player.businesses.randomOrNull(rng) ?: return@EventOption ctx
                    val cost = 3000.0
                    if (cost > ctx.player.cash) ctx else ctx.mapPlayer { p -> p.copy(cash = p.cash - cost) }
                },
                EventOption("patch", "Patch it temporarily", "You did a cheap, temporary fix.") { ctx, rng ->
                    val target = ctx.player.businesses.randomOrNull(rng) ?: return@EventOption ctx
                    ctx.mapPlayer { p ->
                        p.copy(cash = p.cash - 500.0, businesses = p.businesses.map { if (it.id == target.id) it.copy(reputation = (it.reputation - 4.0).coerceAtLeast(0.0)) else it })
                    }
                }
            )
        ),

        // ---------------- PERSONAL / OPPORTUNITY ----------------
        EventDefinition(
            id = "job_offer", category = EventCategory.PERSONAL,
            title = "A Recruiter Reaches Out",
            description = "A company has offered you a position with a signing bonus if you join immediately.",
            educationalNote = "Career moves are a form of investment in yourself: a higher salary now can compound for years through raises, but changing jobs can also mean giving up tenure and stability.",
            weight = { 1.1 },
            options = listOf(
                EventOption("accept", "Accept the signing bonus and switch", "You took the new job and its signing bonus.") { ctx, rng ->
                    val skillsByType = com.prosperity.game.engine.player.SkillType.entries.associateWith { ctx.player.skill(it) }
                    val eligible = JobCatalog.jobs.filter { JobCatalog.isEligible(it, ctx.player.educationLevel, skillsByType) && it.baseSalary > (JobCatalog.byId(ctx.player.currentJobId)?.baseSalary ?: 0.0) }
                    val job = eligible.randomOrNull(rng) ?: return@EventOption ctx
                    ctx.mapPlayer { it.copy(currentJobId = job.id, jobMonthsHeld = 0, cash = it.cash + 1000.0) }
                },
                EventOption("decline", "Decline and stay", "You stayed in your current role.", noOp)
            )
        ),
        EventDefinition(
            id = "medical_expense", category = EventCategory.PERSONAL,
            title = "Unexpected Medical Bill",
            description = "A minor health issue has led to an unexpected medical bill.",
            educationalNote = "Emergency expenses are a key reason financial advisors recommend an emergency fund — cash set aside specifically so a surprise bill doesn't force you to sell investments at a bad time.",
            weight = { 1.0 },
            options = listOf(
                EventOption("pay_cash", "Pay from savings", "You paid the bill from your savings.") { ctx, _ ->
                    val cost = 1200.0
                    if (ctx.player.bankSavings >= cost) ctx.mapPlayer { it.copy(bankSavings = it.bankSavings - cost) }
                    else ctx.mapPlayer { it.copy(cash = it.cash - cost) }
                },
                EventOption("loan", "Put it on a high-interest loan", "You covered it with a short-term loan.") { ctx, _ ->
                    com.prosperity.game.engine.player.PlayerActions.takePersonalLoan(ctx.player, 1200.0, ctx.economy.interestRate + 8.0, 12).let {
                        if (it is com.prosperity.game.engine.player.ActionResult.Success) ctx.copy(player = it.player) else ctx
                    }
                }
            )
        ),
        EventDefinition(
            id = "car_repair", category = EventCategory.PERSONAL,
            title = "Your Car Needs Urgent Repairs",
            description = "Your vehicle broke down and needs repairs to keep commuting to work.",
            educationalNote = "Small, recurring expenses like transportation and maintenance are easy to underestimate when budgeting — they add up to a meaningful share of most households' spending.",
            weight = { 1.0 },
            options = listOf(
                EventOption("repair", "Pay for the repair", "You paid $650 to fix your car.") { ctx, _ -> ctx.mapPlayer { it.copy(cash = it.cash - 650.0) } },
                EventOption("skip_work", "Skip work until you can afford it", "You missed work, taking a small happiness and reputation hit.") { ctx, _ ->
                    ctx.mapPlayer { it.copy(happiness = (it.happiness - 6.0).coerceAtLeast(0.0), reputation = (it.reputation - 2.0).coerceAtLeast(0.0)) }
                }
            )
        ),
        EventDefinition(
            id = "investment_tip", category = EventCategory.OPPORTUNITY,
            title = "A Friend Shares a 'Hot Stock Tip'",
            description = "An acquaintance swears a particular stock is about to take off.",
            educationalNote = "Unverified tips are not the same as research. Professional investors look at company financials, valuations, and market trends rather than acting purely on rumors — 'hot tips' are exactly how many amateur investors lose money.",
            weight = { 1.0 },
            options = listOf(
                EventOption("invest", "Invest a small amount anyway", "You bought a small position just in case.") { ctx, rng ->
                    val stock = ctx.markets.stocks.random(rng)
                    val budget = (ctx.player.cash * 0.05)
                    val shares = (budget / stock.price).toInt()
                    if (shares <= 0) ctx else ctx.mapPlayer { p -> p.copy(cash = p.cash - shares * stock.price, stockHoldings = p.stockHoldings + (stock.id to (p.stockHoldings[stock.id] ?: 0) + shares)) }
                },
                EventOption("skip", "Do your own research first", "You decided not to act on an unverified tip.", noOp)
            )
        ),
        EventDefinition(
            id = "windfall", category = EventCategory.OPPORTUNITY,
            title = "A Small Windfall",
            description = "An old relative left you a modest inheritance.",
            educationalNote = "What you do with a lump sum matters: spending it provides short-term happiness, while investing or paying down high-interest debt can compound into much more over time — this is the essence of the time value of money.",
            weight = { 0.5 },
            options = listOf(
                EventOption("invest", "Invest it in bonds", "You bought government bonds with the windfall.") { ctx, _ ->
                    val bond = ctx.markets.bond("BND_GOV10")
                    val units = (3000.0 / bond.price).toInt().coerceAtLeast(1)
                    ctx.mapPlayer { p -> p.copy(bondHoldings = p.bondHoldings + (bond.id to (p.bondHoldings[bond.id] ?: 0) + units)) }
                },
                EventOption("spend", "Treat yourself", "You spent it on things you enjoyed.") { ctx, _ ->
                    ctx.mapPlayer { it.copy(cash = it.cash + 3000.0, happiness = (it.happiness + 8.0).coerceAtMost(100.0)) }
                }
            )
        ),
        EventDefinition(
            id = "networking_event", category = EventCategory.OPPORTUNITY,
            title = "Invitation to a Networking Event",
            description = "You've been invited to a paid industry networking event.",
            educationalNote = "Human capital — your skills, reputation, and relationships — is itself a form of asset that can pay dividends throughout a career, much like a financial investment.",
            weight = { 0.8 },
            options = listOf(
                EventOption("attend", "Attend the event", "You built valuable connections.") { ctx, _ ->
                    val cost = 200.0
                    if (cost > ctx.player.cash) ctx else ctx.mapPlayer { it.copy(cash = it.cash - cost, reputation = (it.reputation + 4.0).coerceAtMost(100.0)) }
                },
                EventOption("skip", "Skip it", "You stayed home instead.", noOp)
            )
        ),

        // ---------------- DISASTER ----------------
        EventDefinition(
            id = "natural_disaster", category = EventCategory.DISASTER,
            title = "A Storm Damages Local Property",
            description = "A severe storm has caused damage across the region.",
            educationalNote = "Uninsured physical assets carry real risk. This is why insurance exists — trading a small, certain cost (a premium) for protection against a rare but large loss.",
            weight = { 0.5 },
            options = listOf(
                EventOption("repair", "Pay for repairs", "You paid to repair storm damage to your property.") { ctx, _ ->
                    if (ctx.player.properties.isEmpty()) ctx else ctx.mapPlayer { it.copy(cash = it.cash - 4000.0) }
                },
                EventOption("delay", "Delay repairs", "You postponed repairs, but the property's value will suffer.") { ctx, _ ->
                    if (ctx.player.properties.isEmpty()) ctx
                    else ctx.mapPlayer { p -> p.copy(properties = p.properties.map { it.copy(purchasePrice = it.purchasePrice * 0.95) }) }
                }
            )
        ),
        EventDefinition(
            id = "pandemic_shock", category = EventCategory.DISASTER,
            title = "A Global Health Crisis Emerges",
            description = "A fast-spreading illness is forcing widespread shutdowns, hitting consumer-facing businesses hardest.",
            educationalNote = "Large, sudden shocks (pandemics, wars, financial crises) can override the normal business cycle almost overnight, hitting some sectors (travel, restaurants) far harder than others (healthcare, online services). Diversification across sectors — not just asset types — helps cushion these shocks.",
            weight = { e -> 0.15 },
            options = listOf(
                EventOption("adapt", "Shift businesses toward online sales", "You pivoted your businesses toward remote/online operations.") { ctx, _ ->
                    ctx.mapPlayer { p -> p.copy(businesses = p.businesses.map { it.copy(pricePointMultiplier = (it.pricePointMultiplier * 0.95).coerceAtLeast(0.5)) }) }
                        .mapEconomy { it.copy(phase = BusinessCyclePhase.RECESSION, phaseMonthsElapsed = 0) }
                }.let { opt -> opt.copy(apply = { c, r -> opt.apply(c, r).mapEconomy { e -> e.copy(consumerConfidence = (e.consumerConfidence - 15.0).coerceAtLeast(0.0)) } }) },
                EventOption("ride_out", "Ride it out unchanged", "You kept operations the same and weathered the disruption.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(phase = BusinessCyclePhase.RECESSION, phaseMonthsElapsed = 0, consumerConfidence = (it.consumerConfidence - 20.0).coerceAtLeast(0.0)) }
                }
            )
        ),
        EventDefinition(
            id = "supply_chain_disruption", category = EventCategory.DISASTER,
            title = "Major Supply Chain Disruption",
            description = "A shipping crisis is delaying goods worldwide, driving up costs for anything physical.",
            educationalNote = "Globalized supply chains are efficient but fragile — a single chokepoint (a blocked port, a key factory) can ripple into shortages and price spikes far from where the disruption started.",
            weight = { 0.4 },
            options = listOf(
                EventOption("stockpile", "Stockpile inventory now", "You paid extra to secure inventory before prices rose further.") { ctx, _ ->
                    val cost = 2500.0
                    if (cost > ctx.player.cash) ctx else ctx.mapPlayer { it.copy(cash = it.cash - cost) }
                        .mapEconomy { it.copy(inflationRate = it.inflationRate + 1.0) }
                },
                EventOption("wait", "Wait and hope it resolves", "You didn't act, and costs crept upward.") { ctx, _ ->
                    ctx.mapEconomy { it.copy(inflationRate = it.inflationRate + 1.0) }
                }
            )
        )
    )
}

private fun <T> List<T>.randomOrNull(rng: Random): T? = if (isEmpty()) null else this[rng.nextInt(size)]
