package com.koolikanaku.connect.data

import com.koolikanaku.connect.model.ConnectUser

/** A signed-in server session. Kept so the app stays signed in after it is closed. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: ConnectUser
)

interface SessionStore {
    fun load(): Session?
    fun save(session: Session?)
}

class InMemorySessionStore : SessionStore {
    private var session: Session? = null
    override fun load() = session
    override fun save(session: Session?) { this.session = session }
}
