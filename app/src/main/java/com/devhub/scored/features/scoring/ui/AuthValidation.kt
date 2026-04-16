package com.devhub.scored.ui

import android.util.Patterns

fun isValidEmailAddress(value: String): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()
}

