package pe.rutafija.conductor

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingResult: MethodChannel.Result? = null
    private var pendingBytes: ByteArray? = null
    private val saveRequest = 7047

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "pe.rutafija.conductor/report_files").setMethodCallHandler { call, result ->
            if (call.method != "saveReport") { result.notImplemented(); return@setMethodCallHandler }
            if (pendingResult != null) { result.error("SAVE_IN_PROGRESS", "Hay un guardado en curso.", null); return@setMethodCallHandler }
            val bytes = call.argument<ByteArray>("bytes")
            val fileName = call.argument<String>("fileName") ?: ""
            val mime = call.argument<String>("mimeType") ?: ""
            val extension = fileName.substringAfterLast('.', "")
            val expected = mapOf("csv" to "text/csv", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "pdf" to "application/pdf")
            if (bytes == null || bytes.isEmpty() || bytes.size > 20 * 1024 * 1024 || !Regex("ruta-fija-[0-9a-fA-F-]{36}\\.(csv|xlsx|pdf)").matches(fileName) || expected[extension] != mime) {
                result.error("FILE_NOT_ALLOWED", "Archivo no permitido.", null); return@setMethodCallHandler
            }
            pendingResult = result; pendingBytes = bytes
            try {
                // The system grants one chosen content URI. No token, URL,
                // broad storage permission or persistent URI grant is used.
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                startActivityForResult(intent, saveRequest)
            } catch (_: Exception) { completeError("SAVE_UNAVAILABLE", "No se pudo abrir el selector de destino.") }
        }
    }
    @Deprecated("Required by the Flutter activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != saveRequest) return
        val result = pendingResult ?: return
        val bytes = pendingBytes
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null || bytes == null) {
            pendingBytes?.fill(0); pendingBytes = null; pendingResult = null; result.success(false); return
        }
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                ?: throw IllegalStateException("Destination unavailable")
            result.success(true)
        } catch (_: Exception) {
            // Do not log the URI, bytes, or provider's potentially personal error.
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            result.error("SAVE_FAILED", "No se confirmó el guardado; revise el destino elegido.", null)
        } finally { bytes.fill(0); pendingBytes = null; pendingResult = null }
    }
    private fun completeError(code: String, message: String) {
        pendingResult?.error(code, message, null)
        pendingBytes?.fill(0); pendingBytes = null; pendingResult = null
    }
    override fun onDestroy() {
        completeError("SAVE_INTERRUPTED", "El guardado fue interrumpido; consulte el destino antes de repetirlo.")
        super.onDestroy()
    }
}
