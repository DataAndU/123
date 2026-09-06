package com.prosperity.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prosperity.game.data.GameRepository
import com.prosperity.game.network.SocketManager
import com.prosperity.game.network.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthScreenState {
    object CheckingSession : AuthScreenState()
    object LoggedOut : AuthScreenState()
    object LoggedIn : AuthScreenState()
}

data class OnlineUiState(
    val player: PlayerView? = null,
    val economy: EconomyState? = null,
    val markets: MarketSnapshot? = null,
    val economyHistory: List<EconomyHistoryPoint> = emptyList(),
    val jobs: List<Job> = emptyList(),
    val educationPrograms: List<EducationProgram> = emptyList(),
    val listings: List<Listing> = emptyList(),
    val myListings: List<Listing> = emptyList(),
    val ordersBuying: List<MarketOrder> = emptyList(),
    val ordersSelling: List<MarketOrder> = emptyList(),
    val wallet: WalletResponse? = null,
    val coinProducts: Map<String, Int> = emptyMap(),
    val friends: List<Friend> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val onlineCount: Int = 0,
    val isConnected: Boolean = false,
    val isLoading: Boolean = false
)

class OnlineViewModel(
    private val repository: GameRepository,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineUiState())
    val uiState: StateFlow<OnlineUiState> = _uiState.asStateFlow()

    private val _authState = MutableStateFlow<AuthScreenState>(AuthScreenState.CheckingSession)
    val authState: StateFlow<AuthScreenState> = _authState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        if (repository.isLoggedIn()) {
            onSessionEstablished()
        } else {
            _authState.value = AuthScreenState.LoggedOut
        }
        collectSocketEvents()
    }

    // ---------------- Auth ----------------

    fun register(username: String, email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.register(username, email, password, displayName)
                .onSuccess { auth ->
                    _uiState.update { it.copy(player = auth.player, isLoading = false) }
                    onSessionEstablished()
                }
                .onFailure { fail(it, "Registration failed") }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun login(usernameOrEmail: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.login(usernameOrEmail, password)
                .onSuccess { auth ->
                    _uiState.update { it.copy(player = auth.player, isLoading = false) }
                    onSessionEstablished()
                }
                .onFailure { fail(it, "Login failed") }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setServerUrl(url: String) {
        repository.setServerUrl(url)
    }

    fun getServerUrl(): String = repository.getServerUrl()

    fun logout() {
        socketManager.disconnect()
        repository.logout()
        _uiState.value = OnlineUiState()
        _authState.value = AuthScreenState.LoggedOut
    }

    private fun onSessionEstablished() {
        _authState.value = AuthScreenState.LoggedIn
        socketManager.connect()
        socketManager.joinChannel("global")
        refreshEverything()
    }

    fun refreshEverything() {
        refreshPlayer()
        refreshMarket()
        refreshEconomyHistory()
        refreshJobsAndEducation()
        refreshMarketplace()
        refreshWallet()
        refreshFriends()
        refreshChat("global")
    }

    // ---------------- Refreshers ----------------

    fun refreshPlayer() {
        viewModelScope.launch {
            repository.getMe().onSuccess { p -> _uiState.update { it.copy(player = p) } }.onFailure { fail(it, "Could not load your profile") }
        }
    }

    fun refreshMarket() {
        viewModelScope.launch {
            repository.getMarketSnapshot().onSuccess { snap ->
                _uiState.update { it.copy(economy = snap.economy, markets = snap.markets) }
            }.onFailure { fail(it, "Could not load markets") }
        }
    }

    fun refreshEconomyHistory() {
        viewModelScope.launch {
            repository.getEconomyHistory().onSuccess { h -> _uiState.update { it.copy(economyHistory = h) } }
        }
    }

    fun refreshJobsAndEducation() {
        viewModelScope.launch {
            repository.getJobs().onSuccess { j -> _uiState.update { it.copy(jobs = j) } }
            repository.getEducationPrograms().onSuccess { e -> _uiState.update { it.copy(educationPrograms = e) } }
        }
    }

    fun refreshMarketplace() {
        viewModelScope.launch {
            repository.getListings().onSuccess { l -> _uiState.update { it.copy(listings = l) } }
            repository.getMyListings().onSuccess { l -> _uiState.update { it.copy(myListings = l) } }
            repository.getOrdersBuying().onSuccess { o -> _uiState.update { it.copy(ordersBuying = o) } }
            repository.getOrdersSelling().onSuccess { o -> _uiState.update { it.copy(ordersSelling = o) } }
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            repository.getWallet().onSuccess { w -> _uiState.update { it.copy(wallet = w) } }
            repository.getCoinProducts().onSuccess { p -> _uiState.update { it.copy(coinProducts = p) } }
        }
    }

    fun refreshFriends() {
        viewModelScope.launch {
            repository.getFriends().onSuccess { f -> _uiState.update { it.copy(friends = f) } }
        }
    }

    fun refreshChat(channel: String) {
        viewModelScope.launch {
            repository.getChatHistory(channel).onSuccess { msgs -> _uiState.update { it.copy(chatMessages = msgs) } }
        }
    }

    // ---------------- Career ----------------

    fun applyForJob(jobId: String) = runAction { repository.applyForJob(jobId) }
    fun quitJob() = runAction { repository.quitJob() }
    fun trainSkill(skill: String, cost: Double) = runActionMsg({ repository.trainSkill(skill, cost) }) { r -> "Trained ${skill.lowercase()}: +${r.gain}" }
    fun enrollInEducation(programId: String) = runAction { repository.enrollInEducation(programId) }
    fun setLifestyle(tier: String) = runAction { repository.setLifestyle(tier) }

    // ---------------- Personal finance ----------------

    fun takePersonalLoan(amount: Double, annualRate: Double, termMonths: Int) =
        runActionMsg({ repository.takePersonalLoan(amount, annualRate, termMonths) }) { r -> "Loan approved — payment $${"%.2f".format(r.monthlyPayment)}/mo" }
    fun repayPersonalLoan(id: String, amount: Double) = runAction { repository.repayPersonalLoan(id, amount) }
    fun depositSavings(amount: Double) = runAction { repository.depositSavings(amount) }
    fun withdrawSavings(amount: Double) = runAction { repository.withdrawSavings(amount) }
    fun buyProperty(downPaymentFraction: Double, mortgageRate: Double) =
        runActionMsg({ repository.buyProperty(downPaymentFraction, mortgageRate) }) { "Bought property for $${"%,.0f".format(it.price)}" }
    fun sellProperty(id: String) = runActionMsg({ repository.sellProperty(id) }) { "Sold — recovered $${"%,.0f".format(it.proceeds)}" }
    fun buyForeignCurrency(amount: Double) = runAction { repository.buyForeignCurrency(amount) }
    fun sellForeignCurrency(amount: Double) = runAction { repository.sellForeignCurrency(amount) }

    // ---------------- Trading ----------------

    fun tradeStock(stockId: String, side: String, shares: Int) =
        runActionMsg({ repository.tradeStock(stockId, side, shares) }) { r -> "${side.replaceFirstChar { c -> c.uppercase() }} $shares @ $${"%.2f".format(r.executedPrice)}" }
    fun tradeBond(bondId: String, side: String, units: Int) =
        runActionMsg({ repository.tradeBond(bondId, side, units) }) { r -> "${side.replaceFirstChar { c -> c.uppercase() }} $units units @ $${"%.2f".format(r.executedPrice)}" }
    fun tradeCommodity(commodityId: String, side: String, units: Double) =
        runActionMsg({ repository.tradeCommodity(commodityId, side, units) }) { r -> "${side.replaceFirstChar { c -> c.uppercase() }} ${"%.2f".format(units)} units @ $${"%.2f".format(r.executedPrice)}" }

    // ---------------- Business ----------------

    fun startBusiness(type: String, name: String) = runActionMsg({ repository.startBusiness(type, name) }) { "Started $name" }
    fun hireEmployee(id: String) = runAction { repository.hireEmployee(id) }
    fun fireEmployee(id: String) = runAction { repository.fireEmployee(id) }
    fun setBusinessPrice(id: String, multiplier: Double) = runAction { repository.setBusinessPrice(id, multiplier) }
    fun setBusinessAdvertising(id: String, budget: Double) = runAction { repository.setBusinessAdvertising(id, budget) }
    fun upgradeBusiness(id: String) = runAction { repository.upgradeBusiness(id) }
    fun takeBusinessLoan(id: String, amount: Double, annualRate: Double) = runAction { repository.takeBusinessLoan(id, amount, annualRate) }
    fun repayBusinessLoan(id: String, amount: Double) = runAction { repository.repayBusinessLoan(id, amount) }
    fun withdrawFromBusiness(id: String, amount: Double) = runActionMsg({ repository.withdrawFromBusiness(id, amount) }) { "Withdrew $${"%,.2f".format(it.withdrawn)}" }
    fun injectIntoBusiness(id: String, amount: Double) = runAction { repository.injectIntoBusiness(id, amount) }
    fun closeBusiness(id: String) = runActionMsg({ repository.closeBusiness(id) }) { "Closed — recovered $${"%,.2f".format(it.recovered)}" }

    // ---------------- Marketplace ----------------

    fun createListing(businessId: String?, title: String, description: String, priceCoins: Int, quantity: Int) =
        runActionMsg({ repository.createListing(businessId, title, description, priceCoins, quantity) }) { "Listed $title" }
    fun updateListing(id: String, status: String? = null, priceCoins: Int? = null) =
        runAction({ repository.updateListing(id, status, priceCoins) }, refresh = { refreshMarketplace() })
    fun placeOrder(listingId: String, quantity: Int) = runActionMsg({ repository.placeOrder(listingId, quantity) }) { "Order placed" }
    fun advanceOrder(id: String, status: String) = runActionMsg({ repository.advanceOrder(id, status) }) { "Order marked $status" }
    fun cancelOrder(id: String) = runActionMsg({ repository.cancelOrder(id) }) { "Order cancelled" }

    // ---------------- Wallet / billing ----------------

    fun verifyPurchase(productId: String, purchaseToken: String) =
        runActionMsg({ repository.verifyPurchase(productId, purchaseToken) }) { "Credited ${it.coinsCredited} coins" }

    // ---------------- Social ----------------

    fun searchUsers(query: String, onResult: (List<UserSearchResult>) -> Unit) {
        viewModelScope.launch {
            repository.searchUsers(query).onSuccess(onResult).onFailure { fail(it, "Search failed") }
        }
    }

    fun sendFriendRequest(targetUsername: String) = runAction({ repository.sendFriendRequest(targetUsername) }, refresh = { refreshFriends() })
    fun respondToFriendRequest(friendshipId: String, accept: Boolean) = runAction({ repository.respondToFriendRequest(friendshipId, accept) }, refresh = { refreshFriends() })

    fun joinChatChannel(channel: String) {
        socketManager.joinChannel(channel)
        refreshChat(channel)
    }

    fun sendChat(channel: String, body: String) {
        if (body.isBlank()) return
        socketManager.sendChat(channel, body)
    }

    // ---------------- Internal ----------------

    private fun <T> runAction(call: suspend () -> Result<T>) {
        viewModelScope.launch {
            call().onSuccess { refreshPlayer() }.onFailure { fail(it, "Action failed") }
        }
    }

    private fun <T> runAction(call: suspend () -> Result<T>, refresh: () -> Unit) {
        viewModelScope.launch {
            call().onSuccess { refresh() }.onFailure { fail(it, "Action failed") }
        }
    }

    private fun <T> runActionMsg(call: suspend () -> Result<T>, successMessage: (T) -> String) {
        viewModelScope.launch {
            call().onSuccess { value ->
                say(successMessage(value))
                refreshPlayer()
                refreshMarketplace()
            }.onFailure { fail(it, "Action failed") }
        }
    }

    private fun collectSocketEvents() {
        viewModelScope.launch {
            socketManager.connected.collect { connected -> _uiState.update { it.copy(isConnected = connected) } }
        }
        viewModelScope.launch {
            socketManager.economyUpdates.collect { econ -> _uiState.update { it.copy(economy = econ) } }
        }
        viewModelScope.launch {
            socketManager.marketUpdates.collect { mkt -> _uiState.update { it.copy(markets = mkt) } }
        }
        viewModelScope.launch {
            socketManager.tradeExecuted.collect { trade -> say("${trade.username}: ${trade.side} ${trade.instrumentId} @ $${"%.2f".format(trade.executedPrice)}") }
        }
        viewModelScope.launch {
            socketManager.chatMessages.collect { msg -> _uiState.update { it.copy(chatMessages = it.chatMessages + msg) } }
        }
        viewModelScope.launch {
            socketManager.presence.collect { p -> _uiState.update { it.copy(onlineCount = p.count) } }
        }
        viewModelScope.launch {
            socketManager.businessBankrupted.collect { event ->
                if (_uiState.value.player?.businesses?.any { b -> b.id in event.businessIds } == true) {
                    say("One of your businesses went bankrupt and closed.")
                }
                refreshPlayer()
            }
        }
        viewModelScope.launch {
            socketManager.tickComplete.collect {
                refreshPlayer()
                refreshMarket()
            }
        }
        viewModelScope.launch {
            socketManager.orderUpdates.collect { refreshMarketplace() }
        }
    }

    private fun fail(t: Throwable, fallback: String) {
        say(t.message ?: fallback)
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }
}

class OnlineViewModelFactory(
    private val repository: GameRepository,
    private val socketManager: SocketManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = OnlineViewModel(repository, socketManager) as T
}
