package com.minijarvis.app.data

import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class CallLogRepository(private val dao: CallLogEntryDao) {
    fun observeRecent(): Flow<List<CallLogEntryEntity>> = dao.observeRecent()
    suspend fun replaceAll(entries: List<CallLogEntryEntity>) {
        dao.clearAll()
        dao.insertAll(entries)
    }
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)
}

class LocationRepository(private val dao: LocationVisitDao) {
    fun observeRecent(): Flow<List<LocationVisitEntity>> = dao.observeRecent()
    suspend fun logVisit(latitude: Double, longitude: Double, label: String?, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(LocationVisitEntity(latitude = latitude, longitude = longitude, label = label, timestampMillis = atMillis))
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
}

class AppUsageRepository(private val dao: AppUsageDao) {
    fun observeForDay(epochDay: Long = TimeUtils.toEpochDay()): Flow<List<AppUsageEntity>> = dao.observeForDay(epochDay)
    suspend fun replaceForDay(epochDay: Long, entries: List<AppUsageEntity>) {
        dao.clearForDay(epochDay)
        dao.insertAll(entries)
    }
    suspend fun getBetween(startDay: Long, endDay: Long) = dao.getBetween(startDay, endDay)
}

class MusicHistoryRepository(private val dao: MusicPlayDao) {
    fun observeRecent(): Flow<List<MusicPlayEntity>> = dao.observeRecent()
    suspend fun logPlay(title: String, artist: String?, sourceApp: String, atMillis: Long = TimeUtils.nowMillis()) {
        // De-duplicate: the same track reported repeatedly by a notification listener
        // within a short window is one listening session, not a new play.
        val recentDuplicates = dao.countRecentPlays(title, atMillis - DEDUPE_WINDOW_MILLIS)
        if (recentDuplicates == 0) {
            dao.insert(MusicPlayEntity(title = title, artist = artist, sourceApp = sourceApp, timestampMillis = atMillis))
        }
    }
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)

    private companion object {
        const val DEDUPE_WINDOW_MILLIS = 3 * 60 * 1000L
    }
}

class ImageAnalysisRepository(private val dao: ImageAnalysisDao) {
    fun observeAll(): Flow<List<ImageAnalysisEntity>> = dao.observeAll()
    suspend fun add(imageFilePath: String, labels: List<String>, recognizedText: String?, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(
            ImageAnalysisEntity(
                imageFilePath = imageFilePath,
                labelsCsv = labels.joinToString(","),
                recognizedText = recognizedText,
                timestampMillis = atMillis
            )
        )
    suspend fun delete(entity: ImageAnalysisEntity) = dao.delete(entity)
    suspend fun search(keyword: String) = dao.search(keyword)
}
