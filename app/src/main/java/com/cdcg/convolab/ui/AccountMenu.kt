package com.cdcg.convolab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.settings.AccountInitial
import com.cdcg.convolab.journal.appearance.AppearancePanel
import com.cdcg.convolab.journal.input.KeyboardPanel

val LocalJournalNavigationEnabled = compositionLocalOf { true }
val LocalTutorPicker = compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun AccountMenu(name: String, signOut: () -> Unit, openTypingLab: (() -> Unit)?, settings: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var logout by rememberSaveable { mutableStateOf(false) }
    val tutor = LocalTutorPicker.current
    val navigationEnabled = LocalJournalNavigationEnabled.current
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "Account menu" }) { AccountInitial(name) }
        PaperMenu(expanded, { expanded = false }, pointsToAccount = true) {
            PaperMenuGroup("Journal")
            PaperMenuItem("Appearance", JournalSymbol.Appearance) { expanded = false; panel = "Journal appearance" }
            PaperMenuItem("Keyboard", JournalSymbol.Keyboard) { expanded = false; panel = "How would you like to type?" }
            tutor?.let { PaperMenuItem("Choose your tutor", JournalSymbol.Person, enabled = navigationEnabled) { expanded = false; it() } }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            PaperMenuGroup("Account")
            settings?.let { PaperMenuItem("Settings", JournalSymbol.Settings, enabled = navigationEnabled) { expanded = false; it() } }
            PaperMenuItem("Log out", JournalSymbol.Logout, enabled = navigationEnabled) { expanded = false; logout = true }
        }
    }
    panel?.let { title ->
        JournalSheet(title, { panel = null }) {
            if (title == "Journal appearance") AppearancePanel() else KeyboardPanel()
        }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Log out of ConvoLab?") },
        text = { Text("Changes saved on this device will sync when you sign back in to this account.") },
        confirmButton = { TextButton(onClick = { logout = false; signOut() }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}
