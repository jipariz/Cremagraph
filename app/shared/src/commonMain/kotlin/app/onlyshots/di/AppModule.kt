package app.onlyshots.di

import app.onlyshots.Startup
import app.onlyshots.connect.sourceFactory
import app.onlyshots.core.model.ShotHistory
import app.onlyshots.devTools
import app.onlyshots.editor.editorModule
import app.onlyshots.home.HomeViewModel
import app.onlyshots.home.MachineShotsViewModel
import app.onlyshots.record.RecordViewModel
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.ShotSource
import app.onlyshots.sources.SourceHolder
import app.onlyshots.sources.fake.FakeShotSource
import app.onlyshots.sources.machine.MdnsBrowser
import app.onlyshots.sources.machine.NetworkScan
import app.onlyshots.sources.machine.createMachineHttpClient
import app.onlyshots.storage.AppSettingsRepo
import app.onlyshots.storage.Library
import app.onlyshots.storage.ShareTempFiles
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The phones' app: the editor's module plus the machine, the scale and the record and home screens. */
val appModule = module {
    // The machine in use, swapped underneath every screen by the connect flow. A debug build
    // starts on the replayed shot so the record screen can be worked on without a machine; a
    // release build starts disconnected, because claiming a machine nobody owns is a lie.
    single {
        val initial =
            FakeShotSource().apply { if (!devTools) setConnection(ConnectionState.Unreachable("no machine set up")) }
        SourceHolder(CoroutineScope(SupervisorJob() + Dispatchers.Default), initial)
    }
    single<ShotSource> { get<SourceHolder>() }
    // A connected machine keeps a shot history the editor can attach shots from.
    single<ShotHistory> { get<SourceHolder>() }
    single { createMachineHttpClient() }
    // Only Android binds an mDNS browser; elsewhere `.local` names resolve natively and the sweep covers the rest.
    factory { NetworkScan(get(), getOrNull<MdnsBrowser>() ?: MdnsBrowser.None) }
    viewModel { RecordViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { MachineShotsViewModel(get(), get(), get()) }
}

/** Platform bindings: FileSystem, VideoExporter, FrameSampler, PlatformShare, the cache directory, MdnsBrowser. */
expect val platformModule: Module

fun initKoin(platform: KoinAppDeclaration = {}) {
    val koin = startKoin {
        platform()
        modules(editorModule, appModule, platformModule)
    }
        .koin
    // The sidecars are the source of truth: rebuild the index from them, and drop share copies
    // older than a day.
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            koin.get<Library>().reconcile()
        } finally {
            // Read or not, the app has started: the splash never waits on a library that cannot be read.
            Startup.ready = true
        }
        koin.get<ShareTempFiles>().cleanup(Clock.System.now().toEpochMilliseconds())
    }
    // A machine set up earlier is picked up again here; the holder stays paused until the app is
    // in the foreground, so nothing dials before the UI is up.
    sourceFactory(koin.get<AppSettingsRepo>().value, koin.get())?.let { koin.get<SourceHolder>().use(it) }
}
