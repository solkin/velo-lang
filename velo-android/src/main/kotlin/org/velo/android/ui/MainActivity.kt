package org.velo.android.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.velo.android.R
import org.velo.android.databinding.ActivityMainBinding
import org.velo.android.databinding.ItemSampleBinding
import org.velo.android.engine.SampleCatalog
import org.velo.android.engine.SampleInfo

/**
 * The home screen: a card to open your own `.vbc`, then the bundled samples. Mirrors
 * the NanoVM demo's HomeScreen — centered top bar, an elevated "open file" card, a
 * "Samples" section, and one card per sample with a trailing run arrow.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startActivity(TerminalActivity.forFile(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.openFileCard.setOnClickListener { openFile.launch(arrayOf("*/*")) }

        val samples = runCatching { SampleCatalog(assets).samples() }.getOrDefault(emptyList())
        binding.emptyLabel.visibility = if (samples.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        for (sample in samples) addSampleCard(sample)
    }

    private fun applyInsets() {
        val scroll = binding.root.findViewById<android.view.View>(R.id.sampleScroll)
        val baseBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appbar.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = baseBottom + bars.bottom)
            insets
        }
    }

    private fun addSampleCard(sample: SampleInfo) {
        val card = ItemSampleBinding.inflate(layoutInflater, binding.sampleContainer, false)
        card.sampleName.text = sample.name
        card.sampleDescription.text = sample.description
        card.sampleId.text = sample.id
        card.root.setOnClickListener {
            startActivity(TerminalActivity.forSample(this, sample.id, sample.name))
        }
        binding.sampleContainer.addView(card.root)
    }
}
