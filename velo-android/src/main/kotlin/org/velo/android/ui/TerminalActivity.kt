package org.velo.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import org.velo.android.R
import org.velo.android.databinding.ActivityTerminalBinding
import org.velo.android.engine.SampleCatalog
import org.velo.android.engine.VeloTerminalSession
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one compiled Velo program in an on-screen terminal — a View/Material3
 * translation of the NanoVM demo's TerminalScreen: a top bar with a back arrow and
 * an overflow menu, a status row (spinner + state + Stop / Run again), and a
 * scrolling area where program output, echoed input, and system notices are coloured
 * by stream. Launch via [forSample] or [forFile].
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private var session: VeloTerminalSession? = null
    private var bytecode: ByteArray? = null

    // The whole terminal stream as one coloured buffer, appended to incrementally
    // (never rebuilt) so output stays O(total chars), not O(chunks²).
    private val buffer = SpannableStringBuilder()
    private var openSpan: ForegroundColorSpan? = null
    private var openKind: OutputKind? = null
    private var openStart = 0

    // Output chunks produced by the VM worker thread, drained on the UI thread once
    // per frame. A flood of tiny term.print() calls becomes a handful of renders.
    private val pendingLock = Any()
    private val pending = ArrayList<OutputSegment>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val flushScheduled = AtomicBoolean(false)
    private val flushRunnable = Runnable {
        flushScheduled.set(false)
        render()
    }

    private var status: RunStatus = RunStatus.Finished(null)
    private var userStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener(::onMenu)

        binding.statusButton.setOnClickListener {
            if (status is RunStatus.Running) requestStop() else launch()
        }

        binding.input.setOnEditorActionListener { _, actionId, event ->
            val enterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enterUp) { submitInput(); true } else false
        }
        // Tapping the output raises the keyboard, like a real terminal.
        binding.outputScroll.setOnClickListener { if (status is RunStatus.Running) showKeyboard() }
        // Keep the keyboard menu icon in sync with whether the input is focused.
        binding.input.setOnFocusChangeListener { _, hasFocus ->
            val item = binding.toolbar.menu.findItem(R.id.action_keyboard) ?: return@setOnFocusChangeListener
            item.setIcon(if (hasFocus) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard)
            item.setTitle(if (hasFocus) R.string.cd_keyboard_hide else R.string.cd_keyboard_show)
        }

        try {
            bytecode = loadBytecode()
        } catch (e: Exception) {
            enqueue(OutputKind.SYSTEM, "Failed to load program: ${e.message}\n")
            setStatus(RunStatus.Failed(e.message ?: "load error"))
            return
        }
        launch()
    }

    private fun loadBytecode(): ByteArray {
        val sampleId = intent.getStringExtra(EXTRA_SAMPLE_ID)
        if (sampleId != null) return SampleCatalog(assets).bytecode(sampleId)

        val uri = IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java)
            ?: error("no program to run")
        return contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot open file")
    }

    private fun launch() {
        val code = bytecode ?: return
        session?.stop()
        userStopped = false
        clearBuffer()
        render()
        setStatus(RunStatus.Running)

        val s = VeloTerminalSession(
            // Called on the VM worker thread — just enqueue; the UI flush is throttled.
            onOutput = { text -> enqueue(OutputKind.OUT, text) },
            onFinished = { failure, stats ->
                runOnUiThread {
                    when {
                        userStopped -> setStatus(RunStatus.Stopped)
                        failure != null -> {
                            enqueue(OutputKind.SYSTEM, "\n[${failure.message ?: failure.javaClass.simpleName}]\n")
                            setStatus(RunStatus.Failed(failure.message ?: failure.javaClass.simpleName))
                        }
                        else -> setStatus(RunStatus.Finished(stats))
                    }
                }
            },
        )
        session = s
        s.start(code)
    }

    private fun requestStop() {
        userStopped = true
        session?.stop()
    }

    private fun submitInput() {
        if (status !is RunStatus.Running) return
        val text = binding.input.text?.toString() ?: ""
        binding.input.text = null
        enqueue(OutputKind.INPUT, text + "\n") // echo, like a real terminal
        session?.submitInput(text)
    }

    // --- output rendering ---

    /**
     * Queue a chunk of output. Safe to call from any thread (the VM worker calls this
     * directly). A single render is posted to the UI thread per batch, so thousands of
     * tiny `term.print()` calls coalesce into a handful of frames instead of an ANR.
     */
    private fun enqueue(kind: OutputKind, text: String) {
        if (text.isEmpty()) return
        synchronized(pendingLock) { pending.add(OutputSegment(kind, text)) }
        if (flushScheduled.compareAndSet(false, true)) mainHandler.post(flushRunnable)
    }

    /** Drain queued chunks into [buffer], extending the open colour run where possible. */
    private fun flushPending() {
        val chunks: List<OutputSegment>
        synchronized(pendingLock) {
            if (pending.isEmpty()) return
            chunks = ArrayList(pending)
            pending.clear()
        }
        for (c in chunks) {
            val start = buffer.length
            buffer.append(c.text)
            if (c.kind == openKind && openSpan != null) {
                buffer.setSpan(openSpan, openStart, buffer.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val span = ForegroundColorSpan(MaterialColors.getColor(binding.output, c.kind.colorAttr))
                buffer.setSpan(span, start, buffer.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                openSpan = span
                openKind = c.kind
                openStart = start
            }
        }
    }

    private fun clearBuffer() {
        synchronized(pendingLock) { pending.clear() }
        buffer.clear()
        openSpan = null
        openKind = null
        openStart = 0
    }

    /**
     * Renders the stream like a real terminal: split at the LAST newline — everything
     * before it is settled scrollback, the remainder is the "current line" the caret
     * continues from. While running, the current line sits on one row with the inline
     * input field, so the caret picks up exactly where output stopped (same line for a
     * bare prompt, fresh line after a newline).
     */
    private fun render() {
        flushPending()

        val running = status is RunStatus.Running
        binding.inputRow.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE

        if (running) {
            val lastNl = lastNewline(buffer)
            binding.output.text = if (lastNl >= 0) buffer.subSequence(0, lastNl) else ""
            binding.currentLine.text = if (lastNl >= 0) buffer.subSequence(lastNl + 1, buffer.length) else buffer
        } else {
            // No input line when idle/finished — drop a single trailing newline so there
            // is no dangling blank row under the last line.
            binding.output.text =
                if (buffer.isNotEmpty() && buffer.last() == '\n') buffer.subSequence(0, buffer.length - 1) else buffer
        }
        binding.outputScroll.post { binding.outputScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun lastNewline(text: CharSequence): Int {
        for (i in text.length - 1 downTo 0) if (text[i] == '\n') return i
        return -1
    }

    // --- status row ---

    private fun setStatus(newStatus: RunStatus) {
        status = newStatus
        val running = newStatus is RunStatus.Running
        binding.statusSpinner.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE
        binding.statusButton.setText(if (running) R.string.action_stop else R.string.action_run_again)

        binding.statusLabel.text = when (newStatus) {
            RunStatus.Running -> getString(R.string.status_running)
            is RunStatus.Finished -> newStatus.stats?.let {
                getString(R.string.status_finished_stats, it.instructions, it.wallClockMillis)
            } ?: getString(R.string.status_finished)
            RunStatus.Stopped -> getString(R.string.status_stopped)
            is RunStatus.Failed -> getString(R.string.status_failed, newStatus.reason)
        }
        val labelAttr = if (newStatus is RunStatus.Failed) {
            com.google.android.material.R.attr.colorError
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        binding.statusLabel.setTextColor(MaterialColors.getColor(binding.statusLabel, labelAttr))

        render() // refresh scrollback/current-line split and input-row visibility
        if (!running) hideKeyboard()
    }

    // --- menu ---

    private fun onMenu(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_keyboard -> {
            // The focus-change listener swaps the icon/title to match.
            if (binding.input.hasFocus()) {
                hideKeyboard()
                binding.input.clearFocus()
            } else {
                showKeyboard()
            }
            true
        }
        R.id.action_eof -> { session?.endInput(); true }
        R.id.action_share -> { shareOutput(); true }
        R.id.action_clear -> { clearBuffer(); render(); true }
        else -> false
    }

    private fun shareOutput() {
        flushPending()
        val text = buffer.toString()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
    }

    // --- keyboard ---

    private fun showKeyboard() {
        binding.input.requestFocus()
        imm().showSoftInput(binding.input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        imm().hideSoftInputFromWindow(binding.input.windowToken, 0)
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    // --- edge-to-edge insets ---

    private fun applyInsets() {
        val baseBottom = binding.outputScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appbar.updatePadding(top = bars.top)
            // Keep output (and the inline input) clear of the nav bar and, when open, the keyboard.
            binding.outputScroll.updatePadding(bottom = baseBottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(flushRunnable)
        if (isFinishing) session?.stop()
    }

    companion object {
        private const val EXTRA_SAMPLE_ID = "sample_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_URI = "uri"

        fun forSample(context: Context, id: String, name: String): Intent =
            Intent(context, TerminalActivity::class.java)
                .putExtra(EXTRA_SAMPLE_ID, id)
                .putExtra(EXTRA_TITLE, name)

        fun forFile(context: Context, uri: Uri): Intent =
            Intent(context, TerminalActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITLE, fileName(context, uri))

        private fun fileName(context: Context, uri: Uri): String {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
            }
            return uri.lastPathSegment ?: "program.vbc"
        }
    }
}
