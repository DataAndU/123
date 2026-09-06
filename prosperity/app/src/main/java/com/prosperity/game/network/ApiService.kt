package com.prosperity.game.network

import com.prosperity.game.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ---------------- Auth ----------------
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    // ---------------- Player ----------------
    @GET("player/me")
    suspend fun getMe(): Response<PlayerView>

    @POST("player/lifestyle")
    suspend fun setLifestyle(@Body body: LifestyleRequest): Response<OkResponse>

    @GET("player/jobs")
    suspend fun getJobs(): Response<List<Job>>

    @GET("player/education/programs")
    suspend fun getEducationPrograms(): Response<List<EducationProgram>>

    @POST("player/jobs/apply")
    suspend fun applyForJob(@Body body: ApplyJobRequest): Response<OkResponse>

    @POST("player/jobs/quit")
    suspend fun quitJob(): Response<OkResponse>

    @POST("player/skills/train")
    suspend fun trainSkill(@Body body: TrainSkillRequest): Response<TrainSkillResponse>

    @POST("player/education/enroll")
    suspend fun enrollInEducation(@Body body: EnrollRequest): Response<OkResponse>

    @POST("player/loans")
    suspend fun takePersonalLoan(@Body body: TakeLoanRequest): Response<TakeLoanResponse>

    @POST("player/loans/{id}/repay")
    suspend fun repayPersonalLoan(@Path("id") id: String, @Body body: RepayLoanRequest): Response<OkResponse>

    @POST("player/savings/deposit")
    suspend fun depositSavings(@Body body: AmountRequest): Response<OkResponse>

    @POST("player/savings/withdraw")
    suspend fun withdrawSavings(@Body body: AmountRequest): Response<OkResponse>

    @POST("player/properties")
    suspend fun buyProperty(@Body body: BuyPropertyRequest): Response<BuyPropertyResponse>

    @DELETE("player/properties/{id}")
    suspend fun sellProperty(@Path("id") id: String): Response<SellPropertyResponse>

    @POST("player/currency/buy")
    suspend fun buyForeignCurrency(@Body body: AmountRequest): Response<CurrencyBuyResponse>

    @POST("player/currency/sell")
    suspend fun sellForeignCurrency(@Body body: AmountRequest): Response<CurrencySellResponse>

    // ---------------- Market ----------------
    @GET("market/snapshot")
    suspend fun getMarketSnapshot(): Response<MarketSnapshotResponse>

    @GET("market/economy/history")
    suspend fun getEconomyHistory(): Response<List<EconomyHistoryPoint>>

    @POST("trade/stocks")
    suspend fun tradeStock(@Body body: StockTradeRequest): Response<TradeResult>

    @POST("trade/bonds")
    suspend fun tradeBond(@Body body: BondTradeRequest): Response<TradeResult>

    @POST("trade/commodities")
    suspend fun tradeCommodity(@Body body: CommodityTradeRequest): Response<TradeResult>

    // ---------------- Business ----------------
    @POST("business")
    suspend fun startBusiness(@Body body: StartBusinessRequest): Response<BusinessRecord>

    @GET("business/{id}/history")
    suspend fun getBusinessHistory(@Path("id") id: String): Response<List<BusinessHistoryPoint>>

    @POST("business/{id}/hire")
    suspend fun hireEmployee(@Path("id") id: String): Response<OkResponse>

    @POST("business/{id}/fire")
    suspend fun fireEmployee(@Path("id") id: String): Response<OkResponse>

    @POST("business/{id}/price")
    suspend fun setBusinessPrice(@Path("id") id: String, @Body body: PriceRequest): Response<OkResponse>

    @POST("business/{id}/advertise")
    suspend fun setBusinessAdvertising(@Path("id") id: String, @Body body: AdvertiseRequest): Response<OkResponse>

    @POST("business/{id}/upgrade")
    suspend fun upgradeBusiness(@Path("id") id: String): Response<OkResponse>

    @POST("business/{id}/loan")
    suspend fun takeBusinessLoan(@Path("id") id: String, @Body body: BusinessLoanRequest): Response<OkResponse>

    @POST("business/{id}/repay-loan")
    suspend fun repayBusinessLoan(@Path("id") id: String, @Body body: BusinessAmountRequest): Response<OkResponse>

    @POST("business/{id}/withdraw")
    suspend fun withdrawFromBusiness(@Path("id") id: String, @Body body: BusinessAmountRequest): Response<WithdrawResponse>

    @POST("business/{id}/inject")
    suspend fun injectIntoBusiness(@Path("id") id: String, @Body body: BusinessAmountRequest): Response<OkResponse>

    @DELETE("business/{id}")
    suspend fun closeBusiness(@Path("id") id: String): Response<CloseBusinessResponse>

    // ---------------- Marketplace ----------------
    @GET("marketplace/listings")
    suspend fun getListings(): Response<List<Listing>>

    @GET("marketplace/listings/mine")
    suspend fun getMyListings(): Response<List<Listing>>

    @POST("marketplace/listings")
    suspend fun createListing(@Body body: CreateListingRequest): Response<Listing>

    @PATCH("marketplace/listings/{id}")
    suspend fun updateListing(@Path("id") id: String, @Body body: UpdateListingRequest): Response<OkResponse>

    @POST("marketplace/orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): Response<MarketOrder>

    @GET("marketplace/orders/buying")
    suspend fun getOrdersBuying(): Response<List<MarketOrder>>

    @GET("marketplace/orders/selling")
    suspend fun getOrdersSelling(): Response<List<MarketOrder>>

    @POST("marketplace/orders/{id}/advance")
    suspend fun advanceOrder(@Path("id") id: String, @Body body: AdvanceOrderRequest): Response<MarketOrder>

    @POST("marketplace/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") id: String): Response<MarketOrder>

    // ---------------- Wallet & billing ----------------
    @GET("wallet")
    suspend fun getWallet(): Response<WalletResponse>

    @GET("billing/products")
    suspend fun getCoinProducts(): Response<Map<String, Int>>

    @POST("billing/verify-purchase")
    suspend fun verifyPurchase(@Body body: VerifyPurchaseRequest): Response<VerifyPurchaseResponse>

    // ---------------- Social ----------------
    @GET("social/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<UserSearchResult>>

    @POST("social/friends/request")
    suspend fun sendFriendRequest(@Body body: FriendRequestBody): Response<OkResponse>

    @POST("social/friends/respond")
    suspend fun respondToFriendRequest(@Body body: FriendRespondBody): Response<OkResponse>

    @GET("social/friends")
    suspend fun getFriends(): Response<List<Friend>>

    @GET("social/chat/{channel}/history")
    suspend fun getChatHistory(@Path("channel") channel: String): Response<List<ChatMessage>>
}
