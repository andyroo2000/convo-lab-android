package com.cdcg.convolab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cdcg.convolab.ui.JournalBrand

enum class SettingsPage(val title: String) { Overview("Settings"), Profile("Edit profile"), Password("Change password"), WaniKani("WaniKani") }
class SettingsActions(val close: () -> Unit, val signOut: () -> Unit, val typingLab: (() -> Unit)?)

@Composable
fun SettingsScreen(state: SettingsState, controller: SettingsController, actions: SettingsActions) {
    var page by rememberSaveable(state.account.id) { mutableStateOf(SettingsPage.Overview) }
    val close = { if (page == SettingsPage.Overview) actions.close() else { page = SettingsPage.Overview; controller.clearMessage() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = JournalBrand.Paper, contentColor = JournalBrand.Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(page.title, fontFamily = JournalBrand.Serif, fontSize = 34.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = close) { Text("Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SettingsMessages(state)
                    when (page) {
                        SettingsPage.Overview -> SettingsOverview(state, actions) { page = it; controller.clearMessage() }
                        SettingsPage.Profile -> ProfileEditor(state, controller) { if (page == SettingsPage.Profile) page = SettingsPage.Overview }
                        SettingsPage.Password -> PasswordEditor(state, controller) { if (page == SettingsPage.Password) page = SettingsPage.Overview }
                        SettingsPage.WaniKani -> WaniKaniSettings(state, controller)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsMessages(state: SettingsState) {
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    state.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
}

@Composable
private fun SettingsOverview(state: SettingsState, actions: SettingsActions, navigate: (SettingsPage) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf("Account") }
    SettingsChapter("Account", expanded, { expanded = it }) {
        Text(state.account.name, fontFamily = JournalBrand.Serif, fontSize = 25.sp)
        Text(state.account.email)
        TextButton(onClick = { navigate(SettingsPage.Profile) }) { Text("Edit Profile") }
        TextButton(onClick = { navigate(SettingsPage.Password) }) { Text("Change Password") }
        TextButton(onClick = actions.signOut) { Text("Sign Out") }
    }
    SettingsChapter("Connections", expanded, { expanded = it }) {
        TextButton(onClick = { navigate(SettingsPage.WaniKani) }) { Text("WaniKani · " + if (state.known.connected) "Connected" else "Not connected") }
        Text("Sync known kanji to personalize Journal furigana.")
    }
    actions.typingLab?.let { open ->
        SettingsChapter("Experiments", expanded, { expanded = it }) {
            TextButton(onClick = open) { Text("Japanese typing playground") }
        }
    }
}

@Composable
private fun SettingsChapter(title: String, expanded: String, expand: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider()
    TextButton(onClick = { expand(if (title == expanded) "" else title) }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (title == expanded) "Expanded" else "Collapsed" }) {
        Text(title, fontFamily = JournalBrand.Serif, fontSize = 25.sp, modifier = Modifier.weight(1f))
        Text(if (title == expanded) "−" else "+", modifier = Modifier.clearAndSetSemantics {})
    }
    if (title == expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
