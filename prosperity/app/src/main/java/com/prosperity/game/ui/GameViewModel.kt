package com.prosperity.game.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prosperity.game.engine.business.Business
import com.prosperity.game.engine.business.BusinessCatalog
import com.prosperity.game.engine.business.BusinessSimulator
import com.prosperity.game.engine.business.BusinessType
import com.prosperity.game.engine.event.EventDefinition
import com.prosperity.game.engine.game.DifficultyLevel
import com.prosperity.game.engine.game.GameEngine
import com.prosperity.game.engine.game.GameMode
import com.prosperity.game.engine.game.GameState
import com.prosperity.game.engine.game.Leaderboard
import com.prosperity.game.engine.game.LeaderboardEntry
import com.prosperity.game.engine.game.MonthSummary
import com.prosperity.game.engine.player.ActionResult
import com.prosperity.game.engine.player.PlayerState
import com.prosperity.game.save.SaveManager
import com.prosperity.game.save.SaveSlotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val game: GameState? = null,
    val pendingEvent: EventDefinition? = null,
    val lastSummary: MonthSummary? = null,
    val saveSlots: List<SaveSlotInfo> = emptyList(),
    val leaderboard: Leaderboard = Leaderboard()
)

class GameViewModel(private val saveManager: SaveManager) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshSlots()
        refreshLeaderboard()
    }

    // ---------------- Game lifecycle ----------------

    fun startNewGame(mode: GameMode, difficulty: DifficultyLevel, playerName: String) {
        val state = GameEngine.newGame(mode, difficulty, playerName.ifBlank { "Player" })
        updateGame(state)
        say("New game started: ${mode.displayName} (${difficulty.displayName}).")
    }

    fun hasAutosave(): Boolean = saveManager.hasAutosave()

    fun continueAutosave() {
        saveManager.loadAutosave()?.let { updateGame(it, persist = false) }
    }

    fun advanceMonth() {
        val current = _uiState.value.game ?: return
        if (current.isGameOver) return
        val result = GameEngine.advanceMonth(current)
        _uiState.update { it.copy(game = result.state, pendingEvent = result.triggeredEvent, lastSummary = result.summary) }
        persist(result.state)
        if (result.summary.newlyUnlockedAchievements.isNotEmpty()) {
            say("Achievement unlocked: ${result.summary.newlyUnlockedAchievements.joinToString { a -> a.title }}")
        }
        if (result.state.isGameOver) {
            recordLeaderboardEntry(result.state)
            say(result.state.gameOverReason ?: "Game over.")
        }
    }

    fun resolveEvent(optionId: String) {
        val game = _uiState.value.game ?: return
        val event = _uiState.value.pendingEvent ?: return
        val newState = GameEngine.applyEventChoice(game, event, optionId)
        _uiState.update { it.copy(game = newState, pendingEvent = null) }
        persist(newState)
    }

    fun dismissSummary() {
        _uiState.update { it.copy(lastSummary = null) }
    }

    // ---------------- Player financial actions ----------------

    fun applyResult(result: ActionResult) {
        when (result) {
            is ActionResult.Success -> {
                updatePlayer(result.player)
                say(result.message)
            }
            is ActionResult.Failure -> say(result.reason)
        }
    }

    // ---------------- Business actions ----------------

    fun startBusiness(type: BusinessType, name: String) {
        val game = _uiState.value.game ?: return
        val cost = BusinessCatalog.specs.getValue(type).startupCost
        if (cost > game.player.cash) {
            say("Not enough cash — need $${"%,.2f".format(cost)}.")
            return
        }
        val business = BusinessSimulator.startNew(type, name.ifBlank { BusinessCatalog.specs.getValue(type).displayName }, "${game.economy.month}_${game.player.businesses.size}")
        updatePlayer(game.player.copy(cash = game.player.cash - cost, businesses = game.player.businesses + business))
        say("Started ${business.name}.")
    }

    fun updateBusiness(businessId: String, transform: (Business) -> Business) {
        val game = _uiState.value.game ?: return
        updatePlayer(game.player.copy(businesses = game.player.businesses.map { if (it.id == businessId) transform(it) else it }))
    }

    fun withdrawBusinessProfit(businessId: String, amount: Double) {
        val game = _uiState.value.game ?: return
        val business = game.player.businesses.firstOrNull { it.id == businessId } ?: return
        val (updated, taken) = BusinessSimulator.withdrawProfit(business, amount)
        val newPlayer = game.player.copy(
            cash = game.player.cash + taken,
            businesses = game.player.businesses.map { if (it.id == businessId) updated else it }
        )
        updatePlayer(newPlayer)
        say("Withdrew $${"%,.2f".format(taken)} from ${business.name}.")
    }

    fun injectBusinessCapital(businessId: String, amount: Double) {
        val game = _uiState.value.game ?: return
        if (amount > game.player.cash) {
            say("Not enough personal cash.")
            return
        }
        val newPlayer = game.player.copy(
            cash = game.player.cash - amount,
            businesses = game.player.businesses.map { if (it.id == businessId) BusinessSimulator.injectCapital(it, amount) else it }
        )
        updatePlayer(newPlayer)
        say("Injected $${"%,.2f".format(amount)} into the business.")
    }

    fun closeBusiness(businessId: String) {
        val game = _uiState.value.game ?: return
        val business = game.player.businesses.firstOrNull { it.id == businessId } ?: return
        val value = BusinessSimulator.liquidationValue(business)
        updatePlayer(game.player.copy(cash = game.player.cash + value, businesses = game.player.businesses.filterNot { it.id == businessId }))
        say("Closed ${business.name}, recovering $${"%,.2f".format(value)}.")
    }

    // ---------------- Save / load ----------------

    fun refreshSlots() {
        _uiState.update { it.copy(saveSlots = saveManager.listSlots()) }
    }

    fun saveToSlot(slot: Int) {
        val game = _uiState.value.game ?: return
        saveManager.save(slot, game)
        refreshSlots()
        say("Saved to slot $slot.")
    }

    fun loadFromSlot(slot: Int) {
        saveManager.load(slot)?.let { updateGame(it, persist = false) } ?: say("Slot is empty.")
    }

    fun deleteSlot(slot: Int) {
        saveManager.deleteSlot(slot)
        refreshSlots()
    }

    fun exportCurrentGame(uri: Uri) {
        val game = _uiState.value.game ?: return
        val ok = saveManager.exportTo(uri, game)
        say(if (ok) "Save exported." else "Export failed.")
    }

    fun importGame(uri: Uri) {
        val imported = saveManager.importFrom(uri)
        if (imported != null) {
            updateGame(imported, persist = false)
            say("Save imported.")
        } else {
            say("Could not read that file as a Prosperity save.")
        }
    }

    fun refreshLeaderboard() {
        _uiState.update { it.copy(leaderboard = saveManager.loadLeaderboard()) }
    }

    // ---------------- Internal helpers ----------------

    private fun updatePlayer(player: PlayerState) {
        val game = _uiState.value.game ?: return
        updateGame(game.copy(player = player))
    }

    private fun updateGame(state: GameState, persist: Boolean = true) {
        _uiState.update { it.copy(game = state) }
        if (persist) persist(state)
    }

    private fun persist(state: GameState) {
        viewModelScope.launch(Dispatchers.IO) { saveManager.autosave(state) }
    }

    private fun recordLeaderboardEntry(state: GameState) {
        val netWorth = com.prosperity.game.engine.player.FinanceEngine.netWorth(state.player, state.markets)
        saveManager.recordRunEnded(
            LeaderboardEntry(
                playerName = state.playerName,
                gameMode = state.gameMode,
                difficulty = state.difficulty,
                finalNetWorth = netWorth,
                monthsPlayed = state.economy.month,
                tierReached = state.progressionTier,
                endedAtEpochMillis = System.currentTimeMillis(),
                outcome = state.gameOverReason ?: "Ended"
            )
        )
        refreshLeaderboard()
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }
}

class GameViewModelFactory(private val saveManager: SaveManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(saveManager) as T
}
