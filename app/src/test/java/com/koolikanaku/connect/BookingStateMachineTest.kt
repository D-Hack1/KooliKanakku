package com.koolikanaku.connect

import com.koolikanaku.connect.logic.BookingStateMachine
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.logic.BookingStateMachine.Actor
import com.koolikanaku.connect.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingStateMachineTest {

    private fun next(s: BookingStatus, a: Action, who: Actor) = BookingStateMachine.next(s, a, who)

    @Test fun happyPathWithEitherPartyArriving() {
        assertEquals(BookingStatus.ACCEPTED, next(BookingStatus.REQUESTED, Action.ACCEPT, Actor.WORKER))
        assertEquals(BookingStatus.ARRIVED, next(BookingStatus.ACCEPTED, Action.ARRIVE, Actor.WORKER))
        assertEquals(BookingStatus.ARRIVED, next(BookingStatus.ACCEPTED, Action.ARRIVE, Actor.OWNER))
        assertEquals(BookingStatus.IN_PROGRESS, next(BookingStatus.ARRIVED, Action.START, Actor.OWNER))
        assertEquals(BookingStatus.IN_PROGRESS, next(BookingStatus.ARRIVED, Action.START, Actor.WORKER))
        assertEquals(BookingStatus.COMPLETED, next(BookingStatus.IN_PROGRESS, Action.COMPLETE, Actor.WORKER))
        assertEquals(BookingStatus.RATED, next(BookingStatus.COMPLETED, Action.RATE, Actor.OWNER))
    }

    @Test fun onlyTheWorkerAnswersARequest() {
        assertNull(next(BookingStatus.REQUESTED, Action.ACCEPT, Actor.OWNER))
        assertNull(next(BookingStatus.REQUESTED, Action.DECLINE, Actor.OWNER))
        assertEquals(BookingStatus.DECLINED, next(BookingStatus.REQUESTED, Action.DECLINE, Actor.WORKER))
    }

    @Test fun onlyTheOwnerCancelsAnUnansweredRequest() {
        assertEquals(BookingStatus.CANCELLED, next(BookingStatus.REQUESTED, Action.CANCEL, Actor.OWNER))
        assertNull(next(BookingStatus.REQUESTED, Action.CANCEL, Actor.WORKER))
    }

    @Test fun eitherPartyCanCancelAfterAcceptanceButNotAfterArrival() {
        assertEquals(BookingStatus.CANCELLED, next(BookingStatus.ACCEPTED, Action.CANCEL, Actor.OWNER))
        assertEquals(BookingStatus.CANCELLED, next(BookingStatus.ACCEPTED, Action.CANCEL, Actor.WORKER))
        assertNull(next(BookingStatus.ARRIVED, Action.CANCEL, Actor.OWNER))
        assertNull(next(BookingStatus.IN_PROGRESS, Action.CANCEL, Actor.WORKER))
    }

    @Test fun onlyTheTimerExpiresARequest() {
        assertEquals(BookingStatus.EXPIRED, next(BookingStatus.REQUESTED, Action.EXPIRE, Actor.SYSTEM))
        assertNull(next(BookingStatus.REQUESTED, Action.EXPIRE, Actor.OWNER))
        assertNull(next(BookingStatus.ACCEPTED, Action.EXPIRE, Actor.SYSTEM))
    }

    @Test fun onlyTheOwnerRatesAndOnlyAfterCompletion() {
        assertNull(next(BookingStatus.COMPLETED, Action.RATE, Actor.WORKER))
        assertNull(next(BookingStatus.IN_PROGRESS, Action.RATE, Actor.OWNER))
    }

    @Test fun finalStatesAcceptNothing() {
        val finals = listOf(BookingStatus.DECLINED, BookingStatus.EXPIRED, BookingStatus.CANCELLED, BookingStatus.RATED)
        for (s in finals) {
            assertTrue(s.isFinal)
            for (a in Action.values()) for (who in Actor.values()) assertNull("$s $a $who", next(s, a, who))
        }
    }

    @Test fun noStepCanBeSkipped() {
        assertNull(next(BookingStatus.REQUESTED, Action.ARRIVE, Actor.WORKER))
        assertNull(next(BookingStatus.ACCEPTED, Action.START, Actor.WORKER))
        assertNull(next(BookingStatus.ACCEPTED, Action.COMPLETE, Actor.WORKER))
    }

    @Test fun phonesAreRevealedOnlyAfterAcceptance() {
        assertFalse(BookingStatus.REQUESTED.revealsPhones)
        assertFalse(BookingStatus.DECLINED.revealsPhones)
        assertFalse(BookingStatus.CANCELLED.revealsPhones)
        assertTrue(BookingStatus.ACCEPTED.revealsPhones)
        assertTrue(BookingStatus.IN_PROGRESS.revealsPhones)
    }

    @Test fun requestsExpireAfterTheTtl() {
        val ttl = BookingStateMachine.REQUEST_TTL_MS
        assertFalse(BookingStateMachine.isRequestExpired(BookingStatus.REQUESTED, 0, ttl - 1))
        assertTrue(BookingStateMachine.isRequestExpired(BookingStatus.REQUESTED, 0, ttl))
        assertFalse(BookingStateMachine.isRequestExpired(BookingStatus.ACCEPTED, 0, ttl * 10))
    }
}
