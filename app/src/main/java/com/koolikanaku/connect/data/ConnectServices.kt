package com.koolikanaku.connect.data

import android.content.Context
import com.koolikanaku.BuildConfig
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.UserRole
import org.json.JSONObject

/**
 * Picks the backend once for the whole app: the real Supabase project when SUPABASE_URL and
 * SUPABASE_ANON_KEY are set in the .env file at build time, otherwise the in-memory demo backend
 * (sample workers, saved on the phone, nothing sent anywhere).
 */
object ConnectServices {

    private var backend: ConnectBackend? = null

    fun isConfigured(url: String, anonKey: String): Boolean = url.trim().startsWith("https://") && anonKey.trim().length > 20

    @Synchronized
    fun get(context: Context): ConnectBackend = backend ?: create(context.applicationContext).also { backend = it }

    private fun create(context: Context): ConnectBackend =
        if (isConfigured(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)) {
            SupabaseConnectRepository(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY.trim(), SharedPrefsSessionStore(context))
        } else {
            FakeConnectRepository(store = SharedPrefsStateStore(context))
        }
}

private const val PREFS = "kk_connect"

private class SharedPrefsStateStore(context: Context) : FakeConnectRepository.StateStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("demo_state", null)
    override fun save(json: String) { prefs.edit().putString("demo_state", json).apply() }
}

private class SharedPrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Session? = prefs.getString("session", null)?.let { text ->
        runCatching {
            val o = JSONObject(text)
            Session(
                o.getString("access"), o.optString("refresh"),
                ConnectUser(o.getString("uid"), ConnectJson.enumOr(o.getString("role"), UserRole.OWNER), o.getString("name"), o.getString("phone"))
            )
        }.getOrNull()
    }

    override fun save(session: Session?) {
        prefs.edit().apply {
            if (session == null) remove("session")
            else putString("session", JSONObject()
                .put("access", session.accessToken).put("refresh", session.refreshToken)
                .put("uid", session.user.uid).put("role", session.user.role.name)
                .put("name", session.user.name).put("phone", session.user.phone).toString())
        }.apply()
    }
}
