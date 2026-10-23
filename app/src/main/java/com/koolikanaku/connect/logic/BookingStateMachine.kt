package com.koolikanaku.connect.logic

import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.UserRole

/** Who may move a booking where. Pure rules, shared by every backend. */
object BookingStateMachine {

    enum class Action { ACCEPT, DECLINE, CANCEL, EXPIRE, ARRIVE, START, COMPLETE, RATE }

    /** The party doing the action. SYSTEM is the timer. */
    enum class Actor { OWNER, WORKER, SYSTEM }

    const val REQUEST_TTL_MS = 10 * 60 * 1000L

    fun actorFor(role: UserRole): Actor = if (role == UserRole.OWNER) Actor.OWNER else Actor.WORKER

    /** The new status, or null when [actor] may not do [action] from [status]. */
    fun next(status: BookingStatus, action: Action, actor: Actor): BookingStatus? {
        val both = actor == Actor.OWNER || actor == Actor.WORKER
        return when (status) {
            BookingStatus.REQUESTED -> when {
                action == Action.ACCEPT && actor == Actor.WORKER -> BookingStatus.ACCEPTED
                action == Action.DECLINE && actor == Actor.WORKER -> BookingStatus.DECLINED
                action == Action.CANCEL && actor == Actor.OWNER -> BookingStatus.CANCELLED
                action == Action.EXPIRE && actor == Actor.SYSTEM -> BookingStatus.EXPIRED
                else -> null
            }
            BookingStatus.ACCEPTED -> when {
                action == Action.ARRIVE && both -> BookingStatus.ARRIVED
                action == Action.CANCEL && both -> BookingStatus.CANCELLED
                else -> null
            }
            BookingStatus.ARRIVED -> if (action == Action.START && both) BookingStatus.IN_PROGRESS else null
            BookingStatus.IN_PROGRESS -> if (action == Action.COMPLETE && both) BookingStatus.COMPLETED else null
            BookingStatus.COMPLETED -> if (action == Action.RATE && actor == Actor.OWNER) BookingStatus.RATED else null
            else -> null   // final states
        }
    }

    fun isRequestExpired(status: BookingStatus, createdAtMs: Long, nowMs: Long): Boolean =
        status == BookingStatus.REQUESTED && nowMs - createdAtMs >= REQUEST_TTL_MS
}
