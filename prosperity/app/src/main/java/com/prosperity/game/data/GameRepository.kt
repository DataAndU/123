package com.prosperity.game.data

import com.prosperity.game.network.ApiClient
import com.prosperity.game.network.dto.*
import com.prosperity.game.network.safeApiCall

/** Thin pass-through over [ApiClient.service], translating every call into a [Result]. */
class GameRepository(private val apiClient: ApiClient, private val tokenStore: TokenStore) {

    private val api get() = apiClient.service

    fun isLoggedIn(): Boolean = tokenStore.getToken() != null

    fun logout() {
        tokenStore.setToken(null)
    }

    fun setServerUrl(url: String) {
        tokenStore.setServerUrl(url)
        apiClient.rebuild()
    }

    fun getServerUrl(): String = tokenStore.getServerUrl()

    suspend fun register(username: String, email: String, password: String, displayName: String): Result<AuthResponse> =
        safeApiCall { api.register(RegisterRequest(username, email, password, displayName)) }.onSuccess { tokenStore.setToken(it.token) }

    suspend fun login(usernameOrEmail: String, password: String): Result<AuthResponse> =
        safeApiCall { api.login(LoginRequest(usernameOrEmail, password)) }.onSuccess { tokenStore.setToken(it.token) }

    suspend fun getMe(): Result<PlayerView> = safeApiCall { api.getMe() }
    suspend fun setLifestyle(tier: String): Result<OkResponse> = safeApiCall { api.setLifestyle(LifestyleRequest(tier)) }

    suspend fun getJobs(): Result<List<Job>> = safeApiCall { api.getJobs() }
    suspend fun getEducationPrograms(): Result<List<EducationProgram>> = safeApiCall { api.getEducationPrograms() }
    suspend fun applyForJob(jobId: String): Result<OkResponse> = safeApiCall { api.applyForJob(ApplyJobRequest(jobId)) }
    suspend fun quitJob(): Result<OkResponse> = safeApiCall { api.quitJob() }
    suspend fun trainSkill(skill: String, cost: Double): Result<TrainSkillResponse> = safeApiCall { api.trainSkill(TrainSkillRequest(skill, cost)) }
    suspend fun enrollInEducation(programId: String): Result<OkResponse> = safeApiCall { api.enrollInEducation(EnrollRequest(programId)) }

    suspend fun takePersonalLoan(amount: Double, annualRate: Double, termMonths: Int): Result<TakeLoanResponse> =
        safeApiCall { api.takePersonalLoan(TakeLoanRequest(amount, annualRate, termMonths)) }
    suspend fun repayPersonalLoan(id: String, amount: Double): Result<OkResponse> = safeApiCall { api.repayPersonalLoan(id, RepayLoanRequest(amount)) }
    suspend fun depositSavings(amount: Double): Result<OkResponse> = safeApiCall { api.depositSavings(AmountRequest(amount)) }
    suspend fun withdrawSavings(amount: Double): Result<OkResponse> = safeApiCall { api.withdrawSavings(AmountRequest(amount)) }
    suspend fun buyProperty(downPaymentFraction: Double, mortgageRate: Double): Result<BuyPropertyResponse> =
        safeApiCall { api.buyProperty(BuyPropertyRequest(downPaymentFraction, mortgageRate)) }
    suspend fun sellProperty(id: String): Result<SellPropertyResponse> = safeApiCall { api.sellProperty(id) }
    suspend fun buyForeignCurrency(amount: Double): Result<CurrencyBuyResponse> = safeApiCall { api.buyForeignCurrency(AmountRequest(amount)) }
    suspend fun sellForeignCurrency(amount: Double): Result<CurrencySellResponse> = safeApiCall { api.sellForeignCurrency(AmountRequest(amount)) }

    suspend fun getMarketSnapshot(): Result<MarketSnapshotResponse> = safeApiCall { api.getMarketSnapshot() }
    suspend fun getEconomyHistory(): Result<List<EconomyHistoryPoint>> = safeApiCall { api.getEconomyHistory() }
    suspend fun tradeStock(stockId: String, side: String, shares: Int): Result<TradeResult> = safeApiCall { api.tradeStock(StockTradeRequest(stockId, side, shares)) }
    suspend fun tradeBond(bondId: String, side: String, units: Int): Result<TradeResult> = safeApiCall { api.tradeBond(BondTradeRequest(bondId, side, units)) }
    suspend fun tradeCommodity(commodityId: String, side: String, units: Double): Result<TradeResult> =
        safeApiCall { api.tradeCommodity(CommodityTradeRequest(commodityId, side, units)) }

    suspend fun startBusiness(type: String, name: String): Result<BusinessRecord> = safeApiCall { api.startBusiness(StartBusinessRequest(type, name)) }
    suspend fun getBusinessHistory(id: String): Result<List<BusinessHistoryPoint>> = safeApiCall { api.getBusinessHistory(id) }
    suspend fun hireEmployee(id: String): Result<OkResponse> = safeApiCall { api.hireEmployee(id) }
    suspend fun fireEmployee(id: String): Result<OkResponse> = safeApiCall { api.fireEmployee(id) }
    suspend fun setBusinessPrice(id: String, multiplier: Double): Result<OkResponse> = safeApiCall { api.setBusinessPrice(id, PriceRequest(multiplier)) }
    suspend fun setBusinessAdvertising(id: String, monthlyBudget: Double): Result<OkResponse> = safeApiCall { api.setBusinessAdvertising(id, AdvertiseRequest(monthlyBudget)) }
    suspend fun upgradeBusiness(id: String): Result<OkResponse> = safeApiCall { api.upgradeBusiness(id) }
    suspend fun takeBusinessLoan(id: String, amount: Double, annualRate: Double): Result<OkResponse> = safeApiCall { api.takeBusinessLoan(id, BusinessLoanRequest(amount, annualRate)) }
    suspend fun repayBusinessLoan(id: String, amount: Double): Result<OkResponse> = safeApiCall { api.repayBusinessLoan(id, BusinessAmountRequest(amount)) }
    suspend fun withdrawFromBusiness(id: String, amount: Double): Result<WithdrawResponse> = safeApiCall { api.withdrawFromBusiness(id, BusinessAmountRequest(amount)) }
    suspend fun injectIntoBusiness(id: String, amount: Double): Result<OkResponse> = safeApiCall { api.injectIntoBusiness(id, BusinessAmountRequest(amount)) }
    suspend fun closeBusiness(id: String): Result<CloseBusinessResponse> = safeApiCall { api.closeBusiness(id) }

    suspend fun getListings(): Result<List<Listing>> = safeApiCall { api.getListings() }
    suspend fun getMyListings(): Result<List<Listing>> = safeApiCall { api.getMyListings() }
    suspend fun createListing(businessId: String?, title: String, description: String, priceCoins: Int, quantity: Int): Result<Listing> =
        safeApiCall { api.createListing(CreateListingRequest(businessId, title, description, priceCoins, quantity)) }
    suspend fun updateListing(id: String, status: String? = null, priceCoins: Int? = null): Result<OkResponse> =
        safeApiCall { api.updateListing(id, UpdateListingRequest(status, priceCoins)) }
    suspend fun placeOrder(listingId: String, quantity: Int): Result<MarketOrder> = safeApiCall { api.placeOrder(PlaceOrderRequest(listingId, quantity)) }
    suspend fun getOrdersBuying(): Result<List<MarketOrder>> = safeApiCall { api.getOrdersBuying() }
    suspend fun getOrdersSelling(): Result<List<MarketOrder>> = safeApiCall { api.getOrdersSelling() }
    suspend fun advanceOrder(id: String, status: String): Result<MarketOrder> = safeApiCall { api.advanceOrder(id, AdvanceOrderRequest(status)) }
    suspend fun cancelOrder(id: String): Result<MarketOrder> = safeApiCall { api.cancelOrder(id) }

    suspend fun getWallet(): Result<WalletResponse> = safeApiCall { api.getWallet() }
    suspend fun getCoinProducts(): Result<Map<String, Int>> = safeApiCall { api.getCoinProducts() }
    suspend fun verifyPurchase(productId: String, purchaseToken: String): Result<VerifyPurchaseResponse> =
        safeApiCall { api.verifyPurchase(VerifyPurchaseRequest(productId, purchaseToken)) }

    suspend fun searchUsers(query: String): Result<List<UserSearchResult>> = safeApiCall { api.searchUsers(query) }
    suspend fun sendFriendRequest(targetUsername: String): Result<OkResponse> = safeApiCall { api.sendFriendRequest(FriendRequestBody(targetUsername)) }
    suspend fun respondToFriendRequest(friendshipId: String, accept: Boolean): Result<OkResponse> =
        safeApiCall { api.respondToFriendRequest(FriendRespondBody(friendshipId, accept)) }
    suspend fun getFriends(): Result<List<Friend>> = safeApiCall { api.getFriends() }
    suspend fun getChatHistory(channel: String): Result<List<ChatMessage>> = safeApiCall { api.getChatHistory(channel) }
}
