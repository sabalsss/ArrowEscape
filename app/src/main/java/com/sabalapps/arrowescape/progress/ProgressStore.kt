package com.sabalapps.arrowescape.progress

import android.content.Context

/**
 * The tiny slice of key/value storage the progress repository needs. Keeping it
 * behind an interface means the repository — including every parsing and
 * fallback rule — is a plain JVM unit test, with no Android runtime involved.
 */
interface ProgressStore {
    fun getString(key: String): String?

    /** A null [value] removes the key. */
    fun putString(key: String, value: String?)
}

/** Test and preview double. Also the default used by a ViewModel built by hand. */
class InMemoryProgressStore(initial: Map<String, String> = emptyMap()) : ProgressStore {
    private val values = HashMap<String, String>(initial)

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/**
 * The real thing: SharedPreferences, committed synchronously so progress
 * survives the process being killed outright rather than shut down politely.
 *
 * [name] picks the preferences file. Campaign and Endless share the default one
 * under separate keys; the Daily Challenge gets a file of its own, so a corrupt
 * daily save is not even in the same file as the progress it must not be able to
 * damage.
 */
class SharedPrefsProgressStore(
    context: Context,
    name: String = DEFAULT_NAME
) : ProgressStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        // commit(), not apply(): the in-progress board has to be on disk before
        // Android is free to kill us, and these writes are a few dozen bytes.
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
    }

    companion object {
        /** Campaign and Endless progress. */
        const val DEFAULT_NAME = "arrow_escape_progress"

        /** The Daily Challenge, on its own. */
        const val DAILY_NAME = "arrow_escape_daily"

        /** The daily reminder's preference and the rate / share prompt record — retention, not play. */
        const val RETENTION_NAME = "arrow_escape_retention"
    }
}
