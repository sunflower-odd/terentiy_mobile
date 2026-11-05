package ru.terentiy.mobile.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.ZoneRule
import ru.terentiy.mobile.domain.ZoneTrigger
import ru.terentiy.mobile.ui.common.ButtonText
import ru.terentiy.mobile.ui.common.MessageBanner

/**
 * Окно «Режим тревоги» зоны: вход или выход и время, когда правило действует.
 * Время выбирается часами в отдельном окне; промежуток может идти через полночь.
 */
@Composable
internal fun ZoneAlertDialog(editor: AlertEditor, actions: ZonesActions) {
    // Какое время сейчас выбирают: начало, конец или никакое.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = actions.closeAlert,
        title = {
            val title = if (editor.zoneName.isBlank()) stringResource(R.string.zone_alert)
            else stringResource(R.string.zone_alert_title, editor.zoneName)
            Text(title)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTitle(stringResource(R.string.zone_alert_when_person))
                Column(Modifier.selectableGroup()) {
                    Choice(stringResource(R.string.zone_alert_enter), editor.trigger == ZoneTrigger.ENTER) {
                        actions.setAlertTrigger(ZoneTrigger.ENTER)
                    }
                    Choice(stringResource(R.string.zone_alert_exit), editor.trigger == ZoneTrigger.EXIT) {
                        actions.setAlertTrigger(ZoneTrigger.EXIT)
                    }
                }
                SectionTitle(stringResource(R.string.zone_alert_time))
                Column(Modifier.selectableGroup()) {
                    Choice(stringResource(R.string.zone_alert_always), !editor.scheduled) { actions.setAlertScheduled(false) }
                    Choice(stringResource(R.string.zone_alert_scheduled), editor.scheduled) { actions.setAlertScheduled(true) }
                }
                if (editor.scheduled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { picking = FROM }, enabled = !editor.saving, modifier = Modifier.weight(1f)) {
                            ButtonText(stringResource(R.string.zone_alert_from, editor.schedule.from.hhmm()))
                        }
                        OutlinedButton(onClick = { picking = TO }, enabled = !editor.saving, modifier = Modifier.weight(1f)) {
                            ButtonText(stringResource(R.string.zone_alert_to, editor.schedule.to.hhmm()))
                        }
                    }
                    if (editor.schedule.overnight) {
                        Text(
                            text = stringResource(R.string.zone_alert_overnight, editor.schedule.to.hhmm()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                editor.problemRes?.let { MessageBanner(text = stringResource(it)) }
                editor.errorRes?.let { MessageBanner(text = stringResource(it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.saveAlert, enabled = !editor.saving && editor.problemRes == null) {
                Text(stringResource(R.string.zone_alert_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.closeAlert, enabled = !editor.saving) {
                Text(stringResource(R.string.zone_cancel))
            }
        },
    )

    when (picking) {
        FROM -> TimeDialog(editor.schedule.from, onDismiss = { picking = null }) {
            picking = null
            actions.setAlertFrom(it)
        }
        TO -> TimeDialog(editor.schedule.to, onDismiss = { picking = null }) {
            picking = null
            actions.setAlertTo(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.zone_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.zone_cancel)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Строка выбора: касание по всей строке, а не только по кружку. */
@Composable
private fun Choice(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Подпись под зоной: что считается тревогой и когда; у выключенной — что тревог не будет. */
@Composable
internal fun alertSummary(rule: ZoneRule, enabled: Boolean): String {
    if (!enabled) return stringResource(R.string.zone_alert_summary_off)
    val schedule = rule.schedule
    return when {
        schedule == null && rule.trigger == ZoneTrigger.ENTER -> stringResource(R.string.zone_alert_summary_enter_always)
        schedule == null -> stringResource(R.string.zone_alert_summary_exit_always)
        rule.trigger == ZoneTrigger.ENTER ->
            stringResource(R.string.zone_alert_summary_enter_window, schedule.from.hhmm(), schedule.to.hhmm())
        else -> stringResource(R.string.zone_alert_summary_exit_window, schedule.from.hhmm(), schedule.to.hhmm())
    }
}

private val HOURS_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalTime.hhmm(): String = format(HOURS_MINUTES)

private const val FROM = "from"
private const val TO = "to"
