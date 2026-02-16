package com.lsl.agent_browser_kotlin.agent

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import com.lsl.agent_browser_kotlin.WebViewHarnessActivity
import com.lsl.agentbrowser.AgentBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.io.FileInputStream

internal class E2eArtifacts(
    val instrumentation: android.app.Instrumentation,
    private val device: UiDevice,
    private val scenario: ActivityScenario<WebViewHarnessActivity>,
    val runPrefix: String,
    private val framesRelativePath: String,
    val snapshotsRelativePath: String,
    nextStep: Int,
) {
    private var step: Int = nextStep

    fun nextStep(): Int = step

    fun setUiText(text: String) {
        scenario.onActivity { it.setSnapshotText(text) }
    }

    fun captureFrame(): Int {
        val captured = step
        captureStep(
            instrumentation = instrumentation,
            device = device,
            relativePath = framesRelativePath,
            runPrefix = runPrefix,
            step = captured,
        )
        step += 1
        return captured
    }

    fun dumpSnapshotArtifacts(
        snapshotRaw: String,
        snapshotText: String,
        step: Int,
    ) {
        dumpSnapshotArtifacts(
            instrumentation = instrumentation,
            relativePath = snapshotsRelativePath,
            runPrefix = runPrefix,
            step = step,
            snapshotRaw = snapshotRaw,
            snapshotText = snapshotText,
        )
    }

    fun writeTextArtifact(
        displayName: String,
        text: String,
        minBytes: Int = 16,
    ) {
        val uri =
            saveBytesToDownloads(
                instrumentation = instrumentation,
                relativePath = snapshotsRelativePath,
                displayName = displayName,
                mimeType = "text/plain",
                bytes = text.toByteArray(Charsets.UTF_8),
            )
        assertBytesReadable(instrumentation, uri, minBytes = minBytes)
    }
}

internal fun clearOldFrames(instrumentation: android.app.Instrumentation) {
    if (Build.VERSION.SDK_INT < 29) return
    val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (6 * 60 * 60)
    val resolver = instrumentation.targetContext.contentResolver
    val projection = arrayOf(MediaStore.Downloads._ID)
    resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.DATE_ADDED} < ?",
        arrayOf("%agent-browser-kotlin/e2e/frames%", "%step-%.png", cutoffSeconds.toString()),
        null,
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            resolver.delete(uri, null, null)
        }
    }
}

internal fun clearOldSnapshots(instrumentation: android.app.Instrumentation) {
    if (Build.VERSION.SDK_INT < 29) return
    val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (6 * 60 * 60)
    val resolver = instrumentation.targetContext.contentResolver
    val projection = arrayOf(MediaStore.Downloads._ID)
    resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.DATE_ADDED} < ?",
        arrayOf("%agent-browser-kotlin/e2e/snapshots%", "%-snapshot.%", cutoffSeconds.toString()),
        null,
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            resolver.delete(uri, null, null)
        }
    }
}

internal fun captureStep(
    instrumentation: android.app.Instrumentation,
    device: UiDevice,
    relativePath: String,
    runPrefix: String,
    step: Int,
) {
    val stepStr = if (step < 10) "0$step" else step.toString()
    val displayName = "$runPrefix-step-$stepStr.png"

    val tmp = File(instrumentation.targetContext.cacheDir, "tmp-$displayName")
    if (tmp.exists()) tmp.delete()
    val ok = device.takeScreenshot(tmp)
    assertTrue("takeScreenshot failed: ${tmp.absolutePath}", ok)
    assertTrue("tmp screenshot missing: ${tmp.absolutePath}", tmp.exists() && tmp.length() > 0)

    val uri = saveToDownloads(instrumentation, relativePath, displayName, tmp, mimeType = "image/png")
    assertPngReadable(instrumentation, uri)
    tmp.delete()
}

internal fun dumpSnapshotArtifacts(
    instrumentation: android.app.Instrumentation,
    relativePath: String,
    runPrefix: String,
    step: Int,
    snapshotRaw: String,
    snapshotText: String,
) {
    val stepStr = if (step < 10) "0$step" else step.toString()
    val normalizedJson = AgentBrowser.normalizeJsEvalResult(snapshotRaw)

    val textName = "$runPrefix-step-$stepStr-snapshot.txt"
    val textUri =
        saveBytesToDownloads(
            instrumentation = instrumentation,
            relativePath = relativePath,
            displayName = textName,
            mimeType = "text/plain",
            bytes = snapshotText.toByteArray(Charsets.UTF_8),
        )
    assertBytesReadable(instrumentation, textUri, minBytes = 16)

    val jsonName = "$runPrefix-step-$stepStr-snapshot.json"
    val jsonUri =
        saveBytesToDownloads(
            instrumentation = instrumentation,
            relativePath = relativePath,
            displayName = jsonName,
            mimeType = "application/json",
            bytes = normalizedJson.toByteArray(Charsets.UTF_8),
        )
    assertBytesReadable(instrumentation, jsonUri, minBytes = 16)
}

internal fun saveToDownloads(
    instrumentation: android.app.Instrumentation,
    relativePath: String,
    displayName: String,
    sourceFile: File,
    mimeType: String,
): android.net.Uri {
    val resolver = instrumentation.targetContext.contentResolver

    if (Build.VERSION.SDK_INT >= 29) {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed for $displayName")
        resolver.openOutputStream(uri, "w")!!.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        }
        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }.also { resolver.update(uri, it, null, null) }
        return uri
    }

    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val dir = File(downloads, relativePath.removePrefix("Download/"))
    dir.mkdirs()
    val target = File(dir, displayName)
    sourceFile.copyTo(target, overwrite = true)
    return android.net.Uri.fromFile(target)
}

internal fun saveBytesToDownloads(
    instrumentation: android.app.Instrumentation,
    relativePath: String,
    displayName: String,
    mimeType: String,
    bytes: ByteArray,
): android.net.Uri {
    val resolver = instrumentation.targetContext.contentResolver

    if (Build.VERSION.SDK_INT >= 29) {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed for $displayName")
        resolver.openOutputStream(uri, "w")!!.use { out -> out.write(bytes) }
        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }.also { resolver.update(uri, it, null, null) }
        return uri
    }

    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val dir = File(downloads, relativePath.removePrefix("Download/"))
    dir.mkdirs()
    val target = File(dir, displayName)
    target.writeBytes(bytes)
    return android.net.Uri.fromFile(target)
}

internal fun assertPngReadable(instrumentation: android.app.Instrumentation, uri: android.net.Uri) {
    val resolver = instrumentation.targetContext.contentResolver
    val header = ByteArray(8)
    resolver.openInputStream(uri)!!.use { input ->
        val read = input.read(header)
        assertEquals(8, read)
    }
    assertEquals(0x89.toByte(), header[0])
}

internal fun assertBytesReadable(
    instrumentation: android.app.Instrumentation,
    uri: android.net.Uri,
    minBytes: Int,
) {
    val resolver = instrumentation.targetContext.contentResolver
    val buf = ByteArray(64)
    var total = 0
    resolver.openInputStream(uri)!!.use { input ->
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            total += read
            if (total >= minBytes) break
        }
    }
    assertTrue("artifact too small: $uri bytes=$total", total >= minBytes)
}
