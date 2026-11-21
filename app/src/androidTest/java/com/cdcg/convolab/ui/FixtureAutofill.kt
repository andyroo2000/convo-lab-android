package com.cdcg.convolab.ui

import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity

/** Never offer instrumentation credentials to the device owner's password manager. */
fun ComponentActivity.cancelFixtureAutofill() {
    runOnUiThread { getSystemService(AutofillManager::class.java)?.cancel() }
}
