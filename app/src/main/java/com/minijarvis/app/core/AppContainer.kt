package com.minijarvis.app.core

import android.content.Context
import com.minijarvis.app.assistant.AssistantEngine
import com.minijarvis.app.assistant.VoiceOutputManager
import com.minijarvis.app.data.AppDatabase
import com.minijarvis.app.data.AppUsageRepository
import com.minijarvis.app.data.CallLogRepository
import com.minijarvis.app.data.ChatRepository
import com.minijarvis.app.data.ExpenseRepository
import com.minijarvis.app.data.FoodRepository
import com.minijarvis.app.data.HabitRepository
import com.minijarvis.app.data.ImageAnalysisRepository
import com.minijarvis.app.data.LocationRepository
import com.minijarvis.app.data.MedicineRepository
import com.minijarvis.app.data.MusicHistoryRepository
import com.minijarvis.app.data.TaskRepository
import com.minijarvis.app.data.WeightRepository
import com.minijarvis.app.reports.ReportGenerator
import com.minijarvis.app.search.SmartSearchEngine
import com.minijarvis.app.security.PassphraseStore
import com.minijarvis.app.system.AppUsageHelper
import com.minijarvis.app.system.CallLogHelper
import com.minijarvis.app.system.LocationHelper
import com.minijarvis.app.system.ReminderScheduler
import com.minijarvis.app.vision.ImageAnalyzer

/**
 * Hand-rolled, dependency-injection-free composition root.
 *
 * Mini JARVIS deliberately avoids any networking framework — this container
 * simply wires local, on-device components together once at app start.
 */
class AppContainer(private val appContext: Context) {

    val passphraseStore: PassphraseStore by lazy { PassphraseStore(appContext) }
    val database: AppDatabase by lazy { AppDatabase.build(appContext, passphraseStore) }

    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao()) }
    val foodRepository: FoodRepository by lazy { FoodRepository(database.foodDao()) }
    val medicineRepository: MedicineRepository by lazy { MedicineRepository(database.medicineDao()) }
    val weightRepository: WeightRepository by lazy { WeightRepository(database.weightDao()) }
    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao()) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val chatRepository: ChatRepository by lazy { ChatRepository(database.chatDao()) }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(database.callLogEntryDao()) }
    val locationRepository: LocationRepository by lazy { LocationRepository(database.locationVisitDao()) }
    val appUsageRepository: AppUsageRepository by lazy { AppUsageRepository(database.appUsageDao()) }
    val musicHistoryRepository: MusicHistoryRepository by lazy { MusicHistoryRepository(database.musicPlayDao()) }
    val imageAnalysisRepository: ImageAnalysisRepository by lazy { ImageAnalysisRepository(database.imageAnalysisDao()) }

    val callLogHelper: CallLogHelper by lazy { CallLogHelper(appContext) }
    val locationHelper: LocationHelper by lazy { LocationHelper(appContext) }
    val appUsageHelper: AppUsageHelper by lazy { AppUsageHelper(appContext) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val imageAnalyzer: ImageAnalyzer by lazy { ImageAnalyzer() }
    val voiceOutputManager: VoiceOutputManager by lazy { VoiceOutputManager(appContext) }

    val assistantEngine: AssistantEngine by lazy {
        AssistantEngine(
            expenseRepository = expenseRepository,
            foodRepository = foodRepository,
            medicineRepository = medicineRepository,
            weightRepository = weightRepository,
            habitRepository = habitRepository,
            taskRepository = taskRepository,
            chatRepository = chatRepository,
            reminderScheduler = reminderScheduler,
            searchEngine = searchEngine
        )
    }

    val reportGenerator: ReportGenerator by lazy {
        ReportGenerator(
            expenseRepository = expenseRepository,
            foodRepository = foodRepository,
            medicineRepository = medicineRepository,
            weightRepository = weightRepository,
            habitRepository = habitRepository,
            taskRepository = taskRepository
        )
    }

    val searchEngine: SmartSearchEngine by lazy {
        SmartSearchEngine(
            expenseRepository = expenseRepository,
            foodRepository = foodRepository,
            medicineRepository = medicineRepository,
            taskRepository = taskRepository,
            callLogRepository = callLogRepository,
            musicHistoryRepository = musicHistoryRepository,
            imageAnalysisRepository = imageAnalysisRepository
        )
    }

    /** Erases every row of local data and rotates the encryption passphrase. Irreversible. */
    fun eraseAllLocalData() {
        appContext.deleteDatabase(AppDatabase.DATABASE_FILE_NAME)
        passphraseStore.clear()
    }
}
