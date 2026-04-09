package com.example.scorebroadcaster.core.supabase
import android.util.Log
import com.example.scorebroadcaster.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {

    private const val TAG = "SupabaseClientProvider"

    private val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim()

    private val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()

    val missingConfigMessage: String
        get() = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties."

    val clientOrNull by lazy {
        if (!isConfigured) {
            Log.e(TAG, "Supabase client NOT created — SUPABASE_URL or SUPABASE_ANON_KEY is missing/empty in local.properties. All remote operations will be skipped.")
            return@lazy null
        }
        Log.d(TAG, "Creating Supabase client for URL: $supabaseUrl")
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }.also {
            Log.d(TAG, "Supabase client created successfully")
        }
    }
}
