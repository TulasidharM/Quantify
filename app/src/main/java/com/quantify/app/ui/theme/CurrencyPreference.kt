package com.quantify.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

val LocalCurrencyCode = compositionLocalOf { "USD" }

fun localeForCurrencyCode(currencyCode: String): Locale = when (currencyCode) {
    "USD" -> Locale.forLanguageTag("en-US")
    "EUR" -> Locale.forLanguageTag("de-DE")
    "GBP" -> Locale.forLanguageTag("en-GB")
    "INR" -> Locale.forLanguageTag("en-IN")
    "JPY" -> Locale.forLanguageTag("ja-JP")
    "AUD" -> Locale.forLanguageTag("en-AU")
    "CAD" -> Locale.forLanguageTag("en-CA")
    "CHF" -> Locale.forLanguageTag("de-CH")
    "CNY" -> Locale.forLanguageTag("zh-CN")
    "BRL" -> Locale.forLanguageTag("pt-BR")
    else -> Locale.forLanguageTag("en-US")
}
