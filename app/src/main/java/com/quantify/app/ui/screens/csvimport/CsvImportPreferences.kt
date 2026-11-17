package com.quantify.app.ui.screens.csvimport

import androidx.datastore.preferences.core.longPreferencesKey

object CsvImportPreferences {
    val lastPhonePeImportEpoch = longPreferencesKey("phonepe_last_import_epoch")
    val lastBankImportEpoch = longPreferencesKey("bank_statement_last_import_epoch")
}
