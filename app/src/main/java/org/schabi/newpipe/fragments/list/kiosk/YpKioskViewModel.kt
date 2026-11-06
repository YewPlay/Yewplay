/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.kiosk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.home.KioskUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.services.media_ccc.extractors.MediaCCCLiveStreamKiosk
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper

/**
 * YewPlay: state of a home tab (a kiosk of the service: live, music, gaming...). Loading and
 * pagination are NewPipe's, as in the previous KioskFragment and DefaultKioskFragment.
 */
class YpKioskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val historyRecordManager = HistoryRecordManager(application)
    private val disposables = CompositeDisposable()
    private var loadDisposable: Disposable? = null

    private val _state = MutableStateFlow(KioskUiState())
    val state: StateFlow<KioskUiState> = _state.asStateFlow()
    private val _errors = MutableSharedFlow<ErrorInfo>(extraBufferCapacity = 8)

    /** Non-fatal errors, shown as snackbars. */
    val errors: SharedFlow<ErrorInfo> = _errors

    var serviceId: Int = 0
        private set
    var name: String = ""
        private set

    /** The error shown in place of the content, for its buttons. */
    var lastError: ErrorInfo? = null
        private set

    /** Follows the selected service and its default kiosk (the "default kiosk" tab). */
    private var followsSelectedService = false
    private var kioskId: String = ""
    private var url: String = ""
    private var contentCountry: ContentCountry? = null
    private var items: List<StreamInfoItem> = emptyList()
    private var nextPage: Page? = null
    private var initialized = false

    /**
     * Starts the tab once; later calls (e.g. after a rotation) do nothing.
     *
     * @param serviceId the service, ignored when [kioskId] is null
     * @param kioskId the kiosk, or null for the default kiosk of the selected service
     */
    fun init(serviceId: Int, kioskId: String?) {
        if (initialized) {
            return
        }
        initialized = true
        followsSelectedService = kioskId == null
        if (kioskId == null) {
            useDefaultKiosk()
        } else {
            setKiosk(serviceId, kioskId)
        }
        load(forceLoad = false, refreshing = false)
    }

    /** As before: reload when the service (default tab) or the content country changed. */
    fun onResume() {
        if (followsSelectedService && serviceId != ServiceHelper.getSelectedServiceId(app)) {
            useDefaultKiosk()
            load(forceLoad = false, refreshing = false)
        } else if (contentCountry != null && Localization.getPreferredContentCountry(app) != contentCountry) {
            load(forceLoad = true, refreshing = false)
        }
    }

    private fun useDefaultKiosk() {
        val selected = ServiceHelper.getSelectedServiceId(app)
        runCatching { NewPipe.getService(selected).kioskList.defaultKioskId }
            .onSuccess { setKiosk(selected, it) }
            .onFailure { error ->
                val info = ErrorInfo(
                    error,
                    UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"
                )
                lastError = info
                _state.update { it.copy(isLoading = false, error = YpItems.errorUi(app, info)) }
            }
    }

    private fun setKiosk(serviceId: Int, kioskId: String) {
        this.serviceId = serviceId
        this.kioskId = kioskId
        name = KioskTranslator.getTranslatedKioskName(kioskId, app)
        url = runCatching {
            NewPipe.getService(serviceId).kioskList
                .getListLinkHandlerFactoryByType(kioskId)
                .fromId(kioskId)
                .url
        }.getOrDefault("")
    }

    fun retry() = load(forceLoad = true, refreshing = false)

    fun refresh() = load(forceLoad = true, refreshing = true)

    private fun load(forceLoad: Boolean, refreshing: Boolean) {
        if (url.isEmpty()) {
            return
        }
        lastError = null
        contentCountry = Localization.getPreferredContentCountry(app)
        _state.update {
            if (refreshing) it.copy(isRefreshing = true) else KioskUiState(isLoading = true)
        }
        loadDisposable?.dispose()
        loadDisposable = ExtractorHelper.getKioskInfo(serviceId, url, forceLoad)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleResult) { error ->
                val info = ErrorInfo(error, UserAction.REQUESTED_KIOSK, url, serviceId, url)
                lastError = info
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = YpItems.errorUi(app, info)
                    )
                }
            }
    }

    private fun handleResult(result: KioskInfo) {
        if (result.errors.isNotEmpty()) {
            _errors.tryEmit(ErrorInfo(result.errors, UserAction.REQUESTED_KIOSK, url, result))
        }
        items = result.relatedItems
        nextPage = result.nextPage
        val liveKiosk = result.serviceId == ServiceList.MediaCCC.serviceId &&
            result.id == MediaCCCLiveStreamKiosk.KIOSK_ID
        _state.update {
            it.copy(
                items = items.map(::toUi),
                featuredLabel = app.getString(R.string.yp_home_featured, name),
                isLoading = false,
                isRefreshing = false,
                isEmpty = items.isEmpty(),
                emptyText = if (liveKiosk) app.getString(R.string.no_live_streams) else null,
                hasMore = Page.isValid(nextPage),
                error = null
            )
        }
        loadProgress(items)
    }

    fun loadMore() {
        val page = nextPage
        val current = _state.value
        if (!Page.isValid(page) || current.isLoading || current.isLoadingMore) {
            return
        }
        _state.update { it.copy(isLoadingMore = true) }
        loadDisposable?.dispose()
        loadDisposable = ExtractorHelper.getMoreKioskItems(serviceId, url, page)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleMore) { error ->
                // keep what was loaded; stop asking for more pages
                _state.update { it.copy(isLoadingMore = false, hasMore = false) }
                _errors.tryEmit(ErrorInfo(error, UserAction.REQUESTED_KIOSK, url, serviceId, url))
            }
    }

    private fun handleMore(more: ListExtractor.InfoItemsPage<StreamInfoItem>) {
        if (more.errors.isNotEmpty()) {
            _errors.tryEmit(ErrorInfo(more.errors, UserAction.REQUESTED_KIOSK, url, serviceId, url))
        }
        items = items + more.items
        nextPage = more.nextPage
        _state.update {
            it.copy(
                items = it.items + more.items.map(::toUi),
                isLoadingMore = false,
                hasMore = Page.isValid(nextPage)
            )
        }
        loadProgress(more.items)
    }

    private fun loadProgress(list: List<StreamInfoItem>) {
        disposables.add(
            YpItems.loadProgress(app, historyRecordManager, list)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { progress ->
                        if (progress.isNotEmpty()) {
                            _state.update { state ->
                                state.copy(
                                    items = state.items.map { item ->
                                        progress[item.url]?.let { item.copy(progress = it) } ?: item
                                    }
                                )
                            }
                        }
                    },
                    { }
                )
        )
    }

    private fun toUi(item: StreamInfoItem): YpStreamItem = YpItems.toUi(app, item) as YpStreamItem

    /** The extractor item of a video of the tab. */
    fun streamItemAt(index: Int): StreamInfoItem? = items.getOrNull(index)

    override fun onCleared() {
        loadDisposable?.dispose()
        disposables.dispose()
    }
}
