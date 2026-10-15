package nl.nvict.reader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.nvict.reader.BuildConfig
import nl.nvict.reader.export.PdfPrintAdapter

class MainActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    /** Document dat wacht op een locatie voor "Opslaan als". */
    private var pendingSaveAs: DocState? = null
    private val saveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val state = pendingSaveAs
        pendingSaveAs = null
        if (uri != null && state != null) vm.save(state, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsState()
            NVictTheme(theme) {
                ReaderScreen(vm)
                LaunchedEffect(Unit) {
                    vm.events.collect { handle(it) }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let { vm.open(it) }
    }

    private fun handle(event: UiEvent) {
        when (event) {
            is UiEvent.Toast -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
            is UiEvent.OpenUrl -> runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
            }.onFailure { Toast.makeText(this, event.url, Toast.LENGTH_SHORT).show() }
            is UiEvent.ShareFile -> {
                val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", event.file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = event.mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, event.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, event.title))
            }
            is UiEvent.SaveAs -> {
                pendingSaveAs = event.state
                saveAsLauncher.launch(event.suggestedName)
            }
            is UiEvent.Print -> lifecycleScope.launch {
                PdfPrintAdapter.print(this@MainActivity, event.uri, event.name)
            }
        }
    }
}
