package com.yishenghuang.heartext.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.readium.EpubPreferenceMapper
import com.yishenghuang.heartext.readium.PdfLocatorCodec
import com.yishenghuang.heartext.readium.ReaderSession
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
class ReadiumHostFragment : Fragment() {

    private var bookId: String = ""
    private var navigator: Navigator? = null
    private var directionalAttached = false

    private val _locatorUpdates = MutableSharedFlow<Locator>(replay = 1, extraBufferCapacity = 8)
    val locatorUpdates: SharedFlow<Locator> = _locatorUpdates.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        bookId = requireArguments().getString(ARG_BOOK_ID).orEmpty()
        val app = requireActivity().application as HearTextApp
        val session = app.container.readerSessions[bookId]
        val restoredLocator = parseLocator(savedInstanceState?.getString(STATE_LOCATOR))
        val livePrefs = EpubPreferenceMapper.from(app.container.readerPreferences.settings.value)

        when (session) {
            is ReaderSession.Epub -> {
                childFragmentManager.fragmentFactory =
                    session.navigatorFactory.createFragmentFactory(
                        initialLocator = restoredLocator ?: session.initialLocator,
                        initialPreferences = livePrefs,
                        listener = null
                    )
            }
            is ReaderSession.Pdf -> {
                childFragmentManager.fragmentFactory =
                    session.navigatorFactory.createFragmentFactory(
                        initialLocator = restoredLocator ?: session.initialLocator,
                        initialPreferences = session.initialPreferences,
                        listener = null
                    )
            }
            null -> {
                childFragmentManager.fragmentFactory =
                    PdfNavigatorFragment.createDummyFactory(pdfEngineProvider = PdfiumEngineProvider())
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_readium_host, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as HearTextApp
        val session = app.container.readerSessions[bookId] ?: return

        val tag = NAVIGATOR_TAG
        if (childFragmentManager.findFragmentByTag(tag) == null) {
            childFragmentManager.commitNow {
                when (session) {
                    is ReaderSession.Epub -> add(
                        R.id.readium_navigator_container,
                        EpubNavigatorFragment::class.java,
                        Bundle(),
                        tag
                    )
                    is ReaderSession.Pdf -> add(
                        R.id.readium_navigator_container,
                        PdfNavigatorFragment::class.java,
                        Bundle(),
                        tag
                    )
                }
            }
        }

        navigator = childFragmentManager.findFragmentByTag(tag) as? Navigator
        attachDirectionalIfNeeded(app.container.readerPreferences.settings.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator?.currentLocator?.collect { locator ->
                    _locatorUpdates.emit(locator)
                }
            }
        }

        if (session is ReaderSession.Epub) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    app.container.readerPreferences.settings
                        .debounce(120)
                        .collectLatest { settings ->
                            val epub = navigator as? EpubNavigatorFragment ?: return@collectLatest
                            epub.submitPreferences(EpubPreferenceMapper.from(settings))
                            attachDirectionalIfNeeded(settings)
                        }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        navigator?.currentLocator?.value?.let { outState.putString(STATE_LOCATOR, locatorToJson(it)) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        navigator = null
        directionalAttached = false
        super.onDestroyView()
    }

    private fun attachDirectionalIfNeeded(settings: com.yishenghuang.heartext.data.ReaderSettings) {
        val overflow = navigator as? OverflowableNavigator ?: return
        if (directionalAttached) return
        if (settings.pageTurnEffect == com.yishenghuang.heartext.data.PageTurnEffect.CURL) return
        overflow.addInputListener(
            DirectionalNavigationAdapter(
                navigator = overflow,
                animatedTransition = EpubPreferenceMapper.animatedTurns(settings)
            )
        )
        directionalAttached = true
    }

    fun go(locator: Locator): Boolean = navigator?.go(locator) == true

    companion object {
        private const val STATE_LOCATOR = "currentLocator"
        private const val ARG_BOOK_ID = "bookId"
        private const val NAVIGATOR_TAG = "readium_navigator"

        fun newInstance(bookId: String): ReadiumHostFragment {
            return ReadiumHostFragment().apply {
                arguments = Bundle().apply { putString(ARG_BOOK_ID, bookId) }
            }
        }

        fun locatorToJson(locator: Locator): String = PdfLocatorCodec.encode(locator)

        fun progressionPercent(locator: Locator): Float {
            val total = locator.locations.totalProgression ?: return 0f
            return (total * 100.0).toFloat().coerceIn(0f, 100f)
        }

        fun parseLocator(json: String?): Locator? {
            if (json.isNullOrBlank()) return null
            return runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }
    }
}
