package com.cdcg.convolab.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.cdcg.convolab.ui.*

enum class SettingsPage(val title: String) { Overview("Settings"), Profile("Edit profile"), Password("Change password") }
class SettingsActions(val close: () -> Unit, val signOut: () -> Unit, val typingLab: (() -> Unit)?, val deleteAccount: () -> Unit = {})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, controller: SettingsController, actions: SettingsActions) {
    var page by rememberSaveable(state.account.id) { mutableStateOf(SettingsPage.Overview) }
    val close = { if (page == SettingsPage.Overview) actions.close() else { page = SettingsPage.Overview; controller.clearMessage() } }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JournalStyle.Paper, contentColor = JournalBrand.Ink, dragHandle = {
            Box(Modifier.padding(top = 6.dp, bottom = 10.dp).size(width = 36.dp, height = 5.dp).background(JournalBrand.Ink.copy(alpha = .25f), CircleShape))
        }) {
        ProvideTextStyle(JournalStyle.Body) {
            Column(Modifier.fillMaxHeight(.96f).imePadding().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.title, fontFamily = JournalBrand.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, modifier = Modifier.weight(1f))
                    JournalCloseButton("Close ${page.title}", close)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsMessages(state)
                    when (page) {
                        SettingsPage.Overview -> SettingsOverview(state, controller, actions) { page = it; controller.clearMessage() }
                        SettingsPage.Profile -> ProfileEditor(state, controller) { if (page == SettingsPage.Profile) page = SettingsPage.Overview }
                        SettingsPage.Password -> PasswordEditor(state, controller) { if (page == SettingsPage.Password) page = SettingsPage.Overview }
                    }
                    Spacer(Modifier.height(30.dp))
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
private fun SettingsOverview(state: SettingsState, controller: SettingsController, actions: SettingsActions, navigate: (SettingsPage) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf("Account") }
    SettingsChapter("Account", null, expanded, { expanded = it }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountInitial(state.account.name, 48)
            Text(state.account.name, fontFamily = JournalBrand.Serif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
        }
        Column {
            SettingsAction("Edit Profile", JournalSymbol.Person) { navigate(SettingsPage.Profile) }
            SettingsAction("Change Password", JournalSymbol.Lock) { navigate(SettingsPage.Password) }
            SettingsAction("Sign Out", JournalSymbol.Logout, action = actions.signOut)
            SettingsAction("Delete Account", JournalSymbol.Trash, true, actions.deleteAccount)
        }
    }
    SettingsChapter("Connections", JournalSymbol.Link, expanded, { expanded = it }) {
        var wanikani by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().clickable { wanikani = !wanikani }.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("WaniKani", Modifier.weight(1f))
            JournalIcon(if (wanikani) JournalSymbol.Up else JournalSymbol.Down, color = JournalBrand.Blue)
        }
        if (wanikani) WaniKaniSettings(state, controller)
    }
    actions.typingLab?.let { open ->
        SettingsChapter("Experiments", JournalSymbol.Experiment, expanded, { expanded = it }) {
            SettingsAction("Japanese typing playground", JournalSymbol.Keyboard, action = open)
        }
    }
}

@Composable
private fun SettingsAction(title: String, icon: JournalSymbol, destructive: Boolean = false, action: () -> Unit) {
    val color = if (destructive) JournalStyle.Red else JournalBrand.Ink
    Row(Modifier.fillMaxWidth().clickable(onClick = action).heightIn(min = 45.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        JournalIcon(icon, color = color)
        Text(title, Modifier.weight(1f), color = color)
        JournalIcon(JournalSymbol.Chevron, Modifier.size(12.dp), color)
    }
}

@Composable
private fun SettingsChapter(title: String, icon: JournalSymbol?, expanded: String, expand: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = JournalBrand.Ink.copy(alpha = .15f))
    Row(Modifier.fillMaxWidth().clickable { expand(if (title == expanded) "" else title) }
        .semantics { stateDescription = if (title == expanded) "Expanded" else "Collapsed" }
        .heightIn(min = 56.dp).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { JournalIcon(it, color = JournalStyle.Green) }
        Text(title, fontFamily = JournalBrand.Serif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, modifier = Modifier.weight(1f))
        JournalIcon(if (title == expanded) JournalSymbol.Up else JournalSymbol.Down, Modifier.size(13.dp), JournalBrand.Ink.copy(alpha = .5f))
    }
    if (title == expanded) Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun AccountInitial(name: String, size: Int = 44) {
    Box(Modifier.size(size.dp).background(JournalStyle.Green, CircleShape).padding(3.dp).background(JournalBrand.Paper, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.codePoints().findFirst().orElse(63).let { String(Character.toChars(it)).uppercase() },
            fontFamily = JournalBrand.Anton, fontSize = (size * .6).sp, color = JournalBrand.Ink)
    }
}
