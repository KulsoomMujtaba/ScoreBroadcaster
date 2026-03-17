package com.example.scorebroadcaster

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    val client by lazy {
        require(BuildConfig.SUPABASE_URL.isNotEmpty()) {
            "SUPABASE_URL is not set. Add it to local.properties."
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()) {
            "SUPABASE_ANON_KEY is not set. Add it to local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
