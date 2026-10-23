package com.koolikanaku.connect.logic

import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.connect.model.WorkerListing

/** Turns raw listings into the sorted cards an owner sees. */
object NearbyRanking {

    /** One kilometre of distance costs this many stars of ranking. */
    const val KM_PENALTY_STARS = 0.15
    const val DEFAULT_RADIUS_KM = 8.0

    fun rank(
        listings: List<WorkerListing>,
        owner: Geo.LatLng,
        nowMs: Long,
        radiusKm: Double = DEFAULT_RADIUS_KM,
        service: ServiceType? = null
    ): List<WorkerCard> =
        listings.asSequence()
            .filter { it.availability.isVisibleAt(nowMs) }
            .filter { service == null || service in it.profile.services || ServiceType.GENERAL in it.profile.services }
            .mapNotNull { l ->
                val km = Geo.distanceKm(owner, Geo.decodeCenter(l.availability.geohash!!))
                if (km > radiusKm) null
                else WorkerCard(l.profile, l.stats, TrustScore.score(l.stats), km, Geo.distanceBand(km))
            }
            .sortedByDescending { it.trustScore - KM_PENALTY_STARS * it.distanceKm }
            .toList()
}
