package com.koolikanaku.connect.data

import android.content.Context

/**
 * Picks the real Supabase backend when it's configured (BuildConfig.SUPABASE_URL/ANON_KEY, set
 * from local.properties - never committed) and falls back to the on-device demo backend otherwise,
 * so the app still demos with zero setup (extra_feature.md Section 16). Both backends implement
 * the exact same interfaces, so nothing above this file needs to know which one is active.
 */
object ConnectBackend {

    val active: Any by lazy { if (SupabaseHttp.isConfigured) SupabaseConnectRepository else DemoConnectRepository }

    val auth: AuthRepository get() = active as AuthRepository
    val workers: WorkerRepository get() = active as WorkerRepository
    val bookings: BookingRepository get() = active as BookingRepository
    val ratings: RatingRepository get() = active as RatingRepository

    fun init(context: Context) {
        when (val backend = active) {
            is SupabaseConnectRepository -> backend.init(context)
            DemoConnectRepository -> DemoConnectRepository.init(context)
        }
    }
}
