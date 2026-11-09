package com.szczurek9.mediabar.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.szczurek9.mediabar.MusicBarApp
import com.szczurek9.mediabar.permissions.PermissionManager
import com.szczurek9.mediabar.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MusicBarSettingsRoot(prefs: PreferencesRepository, refreshTick: Int) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsContent(prefs, refreshTick)
        }
    }
}

@Composable
private fun SettingsContent(prefs: PreferencesRepository, refreshTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by prefs.settings.collectAsState(initial = BarSettings())
    val status = remember(refreshTick) { PermissionManager.check(ctx) }
    val controller = (ctx.applicationContext as MusicBarApp).overlayController

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("szczur media bar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        SetupCard(status, ctx)

        if (controller != null) {
            Button(onClick = { controller.unhide() }) { Text("Pokaż pasek") }
        }

        SectionCard("Pasek") {
            SwitchRow("szczur media bar włączony", "Główny przełącznik nakładki", s.enabled) {
                scope.launch { prefs.put(SettingKeys.ENABLED, it) }
            }
            ChoiceRow(
                "Forma paska kompaktowego",
                listOf(BarForm.STANDARD to "Standard", BarForm.SLIM to "Slim", BarForm.BUBBLE to "Bubble"),
                s.form,
            ) { scope.launch { prefs.put(SettingKeys.FORM, it.name) } }
            Text(
                when (s.form) {
                    BarForm.STANDARD -> "Okładka, tytuł, wykonawca i equalizer."
                    BarForm.SLIM -> "Ikona aplikacji, z której słuchasz, i tytuł – wąska pastylka (dobra do paska statusu)."
                    BarForm.BUBBLE -> "Tylko okładka i equalizer."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceRow(
                "Tryb po pojawieniu się",
                listOf(false to "Kompaktowy", true to "Rozszerzony"),
                s.startExpanded,
            ) { scope.launch { prefs.put(SettingKeys.START_EXPANDED, it) } }
            SliderRow("Rozmiar panelu", s.scalePercent, 80..130) {
                scope.launch { prefs.put(SettingKeys.SCALE, it) }
            }
            SliderRow("Przezroczystość tła (nieprzezroczystość)", s.opacityPercent, 40..100) {
                scope.launch { prefs.put(SettingKeys.OPACITY, it) }
            }
        }

        SectionCard("Wygląd") {
            ChoiceRow(
                "Motyw",
                listOf(ThemeMode.SYSTEM to "Systemowy", ThemeMode.LIGHT to "Jasny", ThemeMode.DARK to "Ciemny"),
                s.theme,
            ) { scope.launch { prefs.put(SettingKeys.THEME, it.name) } }
            SwitchRow("Kolory z okładki", "Tło i akcenty dopasowane do okładki albumu", s.dynamicColors) {
                scope.launch { prefs.put(SettingKeys.DYNAMIC_COLORS, it) }
            }
            SwitchRow("Animacje", "Rozwijanie, zmiana utworu, equalizer", s.animations) {
                scope.launch { prefs.put(SettingKeys.ANIMATIONS, it) }
            }
        }

        SectionCard("Pozycja") {
            Text("Pole siatki (pasek staje dokładnie tam; rozwija się w dół, w górę albo na boki zależnie od wiersza)", style = MaterialTheme.typography.bodyLarge)
            PositionGrid(s.anchor, s.vAnchor) { h, v -> scope.launch { prefs.setAnchors(h, v) } }
            SwitchRow("Zablokuj przesuwanie palcem", "Pozycja przypięta do wybranego pola siatki", s.lockPosition) {
                scope.launch { prefs.put(SettingKeys.LOCK, it) }
            }
            SwitchRow(
                "W linii paska statusu",
                "Tylko górny rząd siatki; najlepiej z formą Slim. Panel powiadomień i tak zasłania pasek, gdy jest rozwinięty.",
                s.inStatusBar,
            ) { scope.launch { prefs.put(SettingKeys.IN_STATUS_BAR, it) } }
            Text(
                "Bez blokady pasek można przesuwać palcem – pozycja jest zapamiętywana. Długie przytrzymanie paska ukrywa go do następnego utworu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { scope.launch { prefs.setAnchors(s.anchor, s.vAnchor) } }) { Text("Resetuj przesunięcie") }
        }

        SectionCard("Zachowanie") {
            SwitchRow("Pokazuj automatycznie podczas odtwarzania", "Także po tymczasowym ukryciu, gdy zacznie grać nowy utwór", s.autoShow) {
                scope.launch { prefs.put(SettingKeys.AUTO_SHOW, it) }
            }
            SwitchRow(
                "Ukryj, gdy na ekranie jest aplikacja odtwarzacza",
                if (status.usageAccess) "Np. film w YouTube – pasek zniknie, gdy gra ta sama aplikacja"
                else "Wymaga „Dostępu do danych o użyciu” (przycisk w konfiguracji na górze)",
                s.hideInPlayerApp,
            ) { scope.launch { prefs.put(SettingKeys.HIDE_IN_PLAYER, it) } }
            ChoiceRow(
                "Po zatrzymaniu muzyki",
                listOf(
                    AfterStop.KEEP to "Zostaw",
                    AfterStop.HIDE_AFTER_DELAY to "Ukryj po czasie",
                    AfterStop.HIDE_NOW to "Ukryj od razu",
                ),
                s.afterStop,
            ) { scope.launch { prefs.put(SettingKeys.AFTER_STOP, it.name) } }
            if (s.afterStop == AfterStop.HIDE_AFTER_DELAY) {
                SliderRow("Czas do ukrycia (sekundy)", s.hideDelaySec, 5..120, unit = " s") {
                    scope.launch { prefs.put(SettingKeys.HIDE_DELAY, it) }
                }
            }
        }

        SectionCard("Przypomnienia (szczur reminder)") {
            SwitchRow(
                "Przyjmuj przypomnienia",
                "Pasek pokazuje przypomnienia z aplikacji szczur reminder (też bez grającej muzyki). Wymaga uprawnienia „Wyświetlanie nad innymi aplikacjami” i aktywnego dostępu do powiadomień. Na ekranie blokady przypomnienie pojawia się jako zwykłe powiadomienie.",
                s.acceptReminders,
            ) { scope.launch { prefs.put(SettingKeys.ACCEPT_REMINDERS, it) } }
            Text(
                "Czas wyświetlania, interwały „Przypomnij później” i wygląd kart ustawia się w szczur reminder; rozmiar, motyw, przezroczystość i pozycja pochodzą z ustawień powyżej.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (controller != null) DiagnosticsCard(controller)
    }
}

@Composable
private fun SetupCard(status: PermissionStatus, ctx: Context) {
    val ok = status.ready
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (ok) "Konfiguracja gotowa" else "Wymagana konfiguracja",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatusRow("Wyświetlanie nad innymi aplikacjami", status.overlay, "Przyznaj") {
                PermissionManager.open(ctx, PermissionManager.overlayIntent(ctx))
            }
            StatusRow("Dostęp do powiadomień (odczyt sesji muzycznych)", status.notificationAccess, "Przyznaj") {
                PermissionManager.open(ctx, PermissionManager.notificationAccessIntent(ctx))
            }
            if (!status.notificationAccess) {
                Text(
                    "Jeśli przełącznik jest wyszarzony (APK spoza sklepu): Ustawienia aplikacji → menu ⋮ → „Zezwól na ustawienia z ograniczeniami”, potem wróć i przyznaj dostęp.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { PermissionManager.open(ctx, PermissionManager.appDetailsIntent(ctx)) }) {
                    Text("Ustawienia aplikacji")
                }
            }
            StatusRow("Bateria: bez ograniczeń (zalecane na Motoroli)", status.batteryUnrestricted, "Zmień") {
                PermissionManager.open(ctx, PermissionManager.batteryIntent())
            }
            StatusRow("Dostęp do danych o użyciu (opcjonalnie: auto-ukrywanie w aplikacji odtwarzacza)", status.usageAccess, "Przyznaj") {
                PermissionManager.open(ctx, PermissionManager.usageAccessIntent(ctx))
            }
            StatusRow("Usługa nasłuchu połączona", status.listenerConnected, null) {}
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, actionLabel: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (ok) "OK" else "Brak",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!ok && actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, unit: String = "%", onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("${local.roundToInt()}$unit", style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun PositionGrid(anchor: Anchor, vAnchor: VAnchor, onSelect: (Anchor, VAnchor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VAnchor.values().forEach { v ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Anchor.values().forEach { h ->
                    val selected = h == anchor && v == vAnchor
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onSelect(h, v) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("●", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/** Co zgłasza aktywna sesja – pozwala sprawdzić, czy aplikacja udostępnia shuffle / repeat. */
@Composable
private fun DiagnosticsCard(controller: com.szczurek9.mediabar.overlay.MusicOverlayController) {
    val np by controller.nowPlaying.collectAsState()
    SectionCard("Diagnostyka sesji") {
        if (!np.hasSession) {
            Text("Brak aktywnej sesji multimedialnej.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Aplikacja: ${np.packageName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Shuffle: " + (if (np.shuffleAction != null) "dostępny" else "brak") +
                    " · Repeat: " + (if (np.repeatAction != null) "dostępny" else "brak"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (np.customActionInfo.isEmpty()) "Własne akcje sesji: brak"
                else "Własne akcje sesji:\n" + np.customActionInfo.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
