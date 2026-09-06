package com.minijarvis.app.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FileInfo(val path: String, val isDirectory: Boolean, val sizeBytes: Long, val mimeType: String?)

/**
 * Grants and uses Storage Access Framework tree access instead of the
 * MANAGE_EXTERNAL_STORAGE special permission — the user picks a folder (up
 * to and including the device's top-level storage volume, which most
 * pickers do allow selecting) in Android's own folder picker, the grant is
 * persisted and revocable from this app's own Settings screen or Android's
 * system Settings, and nothing here can reach outside whatever was granted.
 *
 * Reads (list/search/read) are unrestricted once access is granted — no
 * per-call prompt, per the user's explicit choice. Writes and deletes are
 * NOT gated here; the caller (AgentOrchestrator) is responsible for routing
 * those through ConfirmationGate first. This class only executes.
 */
class FileAccessManager(private val context: Context) {

    fun grantIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    fun persistAccess(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun grantedRoots(): List<Uri> =
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }

    fun revokeAccess(treeUri: Uri) {
        context.contentResolver.releasePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun hasAnyAccess(): Boolean = grantedRoots().isNotEmpty()

    private fun roots(): List<DocumentFile> = grantedRoots().mapNotNull { DocumentFile.fromTreeUri(context, it) }

    suspend fun list(pathHint: String?, maxResults: Int = 200): List<FileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileInfo>()
        for (root in roots()) {
            val start = if (pathHint.isNullOrBlank()) root else navigateTo(root, pathHint) ?: continue
            walk(start, results, maxResults, if (pathHint.isNullOrBlank()) "" else pathHint.trim('/'))
            if (results.size >= maxResults) break
        }
        results.take(maxResults)
    }

    suspend fun search(query: String, maxResults: Int = 50): List<FileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileInfo>()
        for (root in roots()) {
            searchWalk(root, query.lowercase(), results, maxResults)
            if (results.size >= maxResults) break
        }
        results.take(maxResults)
    }

    suspend fun readText(path: String, maxBytes: Int = 200_000): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = findByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Not found: $path"))
            val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not open $path"))
            val text = String(bytes.copyOfRange(0, minOf(bytes.size, maxBytes)), Charsets.UTF_8)
            Result.success(if (bytes.size > maxBytes) "$text\n...[truncated]" else text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Executes the write — caller must have already obtained user confirmation. */
    suspend fun writeText(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = roots().firstOrNull() ?: return@withContext Result.failure(IllegalStateException("No folder access granted yet"))
            val trimmed = path.trim('/')
            val lastSlash = trimmed.lastIndexOf('/')
            val parentPath = if (lastSlash >= 0) trimmed.substring(0, lastSlash) else ""
            val fileName = if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
            val parentDir = if (parentPath.isEmpty()) root else navigateTo(root, parentPath, createDirs = true)
                ?: return@withContext Result.failure(IllegalStateException("Could not create folder $parentPath"))
            val target = parentDir.findFile(fileName) ?: parentDir.createFile("text/plain", fileName)
                ?: return@withContext Result.failure(IllegalStateException("Could not create $fileName"))
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(content.toByteArray()) }
                ?: return@withContext Result.failure(IllegalStateException("Could not open $fileName for writing"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Executes the delete — caller must have already obtained user confirmation. */
    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = findByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Not found: $path"))
        if (file.delete()) Result.success(Unit) else Result.failure(IllegalStateException("Delete failed for $path"))
    }

    private fun findByPath(path: String): DocumentFile? {
        for (root in roots()) {
            navigateTo(root, path)?.let { return it }
        }
        return null
    }

    private fun navigateTo(root: DocumentFile, relativePath: String, createDirs: Boolean = false): DocumentFile? {
        var current = root
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val next = current.findFile(segment)
            current = when {
                next != null -> next
                createDirs -> current.createDirectory(segment) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun walk(dir: DocumentFile, results: MutableList<FileInfo>, maxResults: Int, relativePath: String) {
        if (results.size >= maxResults) return
        for (child in dir.listFiles()) {
            if (results.size >= maxResults) return
            val childPath = if (relativePath.isEmpty()) child.name.orEmpty() else "$relativePath/${child.name}"
            results.add(FileInfo(childPath, child.isDirectory, child.length(), child.type))
            if (child.isDirectory) walk(child, results, maxResults, childPath)
        }
    }

    private fun searchWalk(dir: DocumentFile, query: String, results: MutableList<FileInfo>, maxResults: Int, relativePath: String = "") {
        if (results.size >= maxResults) return
        for (child in dir.listFiles()) {
            if (results.size >= maxResults) return
            val childPath = if (relativePath.isEmpty()) child.name.orEmpty() else "$relativePath/${child.name}"
            if (child.name?.contains(query, ignoreCase = true) == true) {
                results.add(FileInfo(childPath, child.isDirectory, child.length(), child.type))
            }
            if (child.isDirectory) searchWalk(child, query, results, maxResults, childPath)
        }
    }
}
