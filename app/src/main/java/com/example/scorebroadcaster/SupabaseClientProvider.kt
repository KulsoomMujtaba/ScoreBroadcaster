package com.example.scorebroadcaster

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    private val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim()

    private val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()

    val missingConfigMessage: String
        get() = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties."

    val clientOrNull by lazy {
        if (!isConfigured) return@lazy null
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
