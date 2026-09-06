package com.prosperity.game.save

import android.content.Context
import android.net.Uri
import com.prosperity.game.engine.game.DifficultyLevel
import com.prosperity.game.engine.game.GameMode
import com.prosperity.game.engine.game.GameState
import com.prosperity.game.engine.game.Leaderboard
import com.prosperity.game.engine.game.LeaderboardEntry
import com.prosperity.game.engine.player.FinanceEngine
import kotlinx.serialization.json.Json
import java.io.File

data class SaveSlotInfo(
    val slot: Int,
    val exists: Boolean,
    val playerName: String? = null,
    val gameMode: GameMode? = null,
    val difficulty: DifficultyLevel? = null,
    val month: Int? = null,
    val netWorth: Double? = null,
    val savedAtEpochMillis: Long? = null
)

/**
 * Entirely local, file-based persistence — no database, no network. A save
 * is one JSON document per slot inside the app's private storage
 * (`context.filesDir`), which the OS deletes only when the app itself is
 * uninstalled.
 */
class SaveManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val savesDir: File by lazy { File(context.filesDir, "saves").apply { mkdirs() } }
    private fun fileForSlot(slot: Int) = File(savesDir, "slot_$slot.json")
    private val autosaveFile: File by lazy { File(savesDir, "autosave.json") }
    private val leaderboardFile: File by lazy { File(savesDir, "leaderboard.json") }

    val slotCount = 5

    fun save(slot: Int, state: GameState) {
        fileForSlot(slot).writeText(json.encodeToString(GameState.serializer(), state))
    }

    fun load(slot: Int): GameState? = readState(fileForSlot(slot))

    fun deleteSlot(slot: Int) {
        fileForSlot(slot).delete()
    }

    fun listSlots(): List<SaveSlotInfo> = (1..slotCount).map { slot ->
        val file = fileForSlot(slot)
        val state = if (file.exists()) readState(file) else null
        if (state == null) SaveSlotInfo(slot, exists = false)
        else SaveSlotInfo(
            slot = slot,
            exists = true,
            playerName = state.playerName,
            gameMode = state.gameMode,
            difficulty = state.difficulty,
            month = state.economy.month,
            netWorth = FinanceEngine.netWorth(state.player, state.markets),
            savedAtEpochMillis = file.lastModified()
        )
    }

    fun autosave(state: GameState) {
        autosaveFile.writeText(json.encodeToString(GameState.serializer(), state))
    }

    fun loadAutosave(): GameState? = if (autosaveFile.exists()) readState(autosaveFile) else null

    fun hasAutosave(): Boolean = autosaveFile.exists()

    /** Writes the save as plain JSON to a user-chosen document (from ACTION_CREATE_DOCUMENT). */
    fun exportTo(uri: Uri, state: GameState): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(GameState.serializer(), state).toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    }.getOrDefault(false)

    /** Reads a save previously exported, from a user-chosen document (from ACTION_OPEN_DOCUMENT). */
    fun importFrom(uri: Uri): GameState? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            json.decodeFromString(GameState.serializer(), input.readBytes().toString(Charsets.UTF_8))
        }
    }.getOrNull()

    fun loadLeaderboard(): Leaderboard = runCatching {
        if (!leaderboardFile.exists()) Leaderboard()
        else json.decodeFromString(Leaderboard.serializer(), leaderboardFile.readText())
    }.getOrDefault(Leaderboard())

    fun recordRunEnded(entry: LeaderboardEntry) {
        val updated = loadLeaderboard().withEntry(entry)
        leaderboardFile.writeText(json.encodeToString(Leaderboard.serializer(), updated))
    }

    private fun readState(file: File): GameState? = runCatching {
        json.decodeFromString(GameState.serializer(), file.readText())
    }.getOrNull()
}
