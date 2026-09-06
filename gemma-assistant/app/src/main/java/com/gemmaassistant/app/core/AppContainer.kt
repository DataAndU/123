package com.gemmaassistant.app.core

import android.content.Context
import com.gemmaassistant.app.assistant.AssistantEngine
import com.gemmaassistant.app.assistant.VoiceOutputManager
import com.gemmaassistant.app.control.AppLauncher
import com.gemmaassistant.app.control.ContactsHelper
import com.gemmaassistant.app.control.PhoneActionsManager
import com.gemmaassistant.app.control.SystemControlManager
import com.gemmaassistant.app.data.AgentActivityRepository
import com.gemmaassistant.app.data.AppDatabase
import com.gemmaassistant.app.data.ChatRepository
import com.gemmaassistant.app.files.FileAccessManager
import com.gemmaassistant.app.llm.ConfirmationGate
import com.gemmaassistant.app.llm.LocalLlmEngine
import com.gemmaassistant.app.llm.ModelManager
import com.gemmaassistant.app.net.WebFetchTool
import com.gemmaassistant.app.security.PassphraseStore
import com.gemmaassistant.app.system.AssistantSettingsStore

/**
 * Hand-rolled, dependency-injection-free composition root — every component
 * is wired together once here, at app start.
 */
class AppContainer(private val appContext: Context) {

    val passphraseStore: PassphraseStore by lazy { PassphraseStore(appContext) }
    val database: AppDatabase by lazy { AppDatabase.build(appContext, passphraseStore) }

    val chatRepository: ChatRepository by lazy { ChatRepository(database.chatDao()) }
    val agentActivityRepository: AgentActivityRepository by lazy { AgentActivityRepository(database.agentActivityLogDao()) }

    val voiceOutputManager: VoiceOutputManager by lazy { VoiceOutputManager(appContext) }

    val appLauncher: AppLauncher by lazy { AppLauncher(appContext) }
    val systemControlManager: SystemControlManager by lazy { SystemControlManager(appContext) }
    val contactsHelper: ContactsHelper by lazy { ContactsHelper(appContext) }
    val phoneActionsManager: PhoneActionsManager by lazy { PhoneActionsManager(appContext, contactsHelper) }

    /** Optional — only ever does anything once the user imports and loads a model; see [ModelManager]. */
    val localLlmEngine: LocalLlmEngine by lazy { LocalLlmEngine(appContext) }
    val modelManager: ModelManager by lazy { ModelManager(appContext) }
    val assistantSettingsStore: AssistantSettingsStore by lazy { AssistantSettingsStore(appContext) }

    /** Agent-only capabilities — unreachable unless a local model is loaded (see AssistantEngine). */
    val fileAccessManager: FileAccessManager by lazy { FileAccessManager(appContext) }
    val webFetchTool: WebFetchTool by lazy { WebFetchTool(assistantSettingsStore) }
    val confirmationGate: ConfirmationGate by lazy { ConfirmationGate() }

    val assistantEngine: AssistantEngine by lazy {
        AssistantEngine(
            appContext = appContext,
            chatRepository = chatRepository,
            appLauncher = appLauncher,
            systemControlManager = systemControlManager,
            phoneActionsManager = phoneActionsManager,
            localLlmEngine = localLlmEngine,
            assistantSettingsStore = assistantSettingsStore,
            fileAccessManager = fileAccessManager,
            webFetchTool = webFetchTool,
            confirmationGate = confirmationGate,
            agentActivityRepository = agentActivityRepository
        )
    }

    /** Erases every row of local data and rotates the encryption passphrase. Irreversible. */
    fun eraseAllLocalData() {
        appContext.deleteDatabase(AppDatabase.DATABASE_FILE_NAME)
        passphraseStore.clear()
    }
}
