package com.localai.cutout.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.cutout.catalog.FeatureCatalog
import com.localai.cutout.core.inference.WhisperLanguages

@Composable
fun SpeechToTextScreen(
    state: AppState,
    onBack: () -> Unit,
    onChooseAudio: () -> Unit,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayAudio: () -> Unit,
    onTranscribe: () -> Unit,
    onCancel: () -> Unit,
    onEditTranscript: (String) -> Unit,
    onSave: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val timingGroups = remember(state.transcriptMarks) {
        state.transcriptMarks.groupBy { (it.timeSeconds.toInt() / 5) * 5 }.toList()
    }
    LaunchedEffect(state.transcriptionRecording) {
        if (state.transcriptionRecording) {
            recordingSeconds = 0
            while (true) {
                kotlinx.coroutines.delay(1_000)
                recordingSeconds++
                if (recordingSeconds >= 300) { onStopRecording(); break }
            }
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Column {
                    Text("DAPPLE AUDIO TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                    Text("Speech-to-Text", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your words, ready to use.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Transcribe multilingual recordings on your phone. Audio isn't uploaded. Files over five minutes aren't supported yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TRANSCRIPTION LANGUAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                Box {
                    OutlinedButton(
                        onClick = { languageMenuExpanded = true },
                        enabled = !state.transcriptionBusy && !state.transcriptionRecording,
                    ) {
                        Text(if (state.transcriptionLanguageSelection == WhisperLanguages.AUTO) "Auto-detect" else WhisperLanguages.label(state.transcriptionLanguageSelection) ?: "Auto-detect")
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose transcription language")
                    }
                    DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Auto-detect") },
                            onClick = { languageMenuExpanded = false; onLanguageSelected(WhisperLanguages.AUTO) },
                        )
                        WhisperLanguages.supported.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.name) },
                                onClick = { languageMenuExpanded = false; onLanguageSelected(language.code) },
                            )
                        }
                    }
                }
                Text("Options follow Whisper's supported language list. Recognition accuracy varies by language and recording quality.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(state.transcriptionName ?: "No recording selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (state.transcriptionSource == null) "Choose a recording or use your microphone" else "Ready to transcribe offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onChooseAudio, enabled = !state.transcriptionBusy && !state.transcriptionRecording, modifier = Modifier.weight(1f)) { Text("Choose audio") }
                        Button(onClick = if (state.transcriptionRecording) onStopRecording else onRecord, enabled = !state.transcriptionBusy, modifier = Modifier.weight(1f)) {
                            Icon(if (state.transcriptionRecording) Icons.Rounded.Pause else Icons.Rounded.Mic, null)
                            Spacer(Modifier.size(7.dp))
                            Text(if (state.transcriptionRecording) "Stop · ${formatRecordingTime(recordingSeconds)}" else "Record")
                        }
                    }
                    if (state.transcriptionSource != null && !state.transcriptionRecording) {
                        OutlinedButton(onClick = onPlayAudio, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (state.audioPlaying == "transcription") Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.audioPlaying == "transcription") "Stop playback" else "Play recording")
                        }
                    }
                    if (state.transcriptionSource != null && state.transcriptText.isEmpty()) Button(
                        onClick = onTranscribe,
                        enabled = state.transcriptionInstalled && !state.transcriptionBusy && !state.transcriptionRecording,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(if (state.transcriptionBusy) "Transcribing…" else if (state.transcriptionInstalled) "Transcribe recording" else "Download model in Explore") }
                    if (state.transcriptionBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(state.transcriptionStage ?: "Working on your recording…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onCancel) { Text("Cancel") }
                        }
                        LinearProgressIndicator(progress = { state.transcriptionProgress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Audio sections complete · ${state.transcriptionProgress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.transcriptionRecording) Text("Recording stays on this phone. Stop when you're done.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (state.transcriptionError != null) item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(state.transcriptionError, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (state.transcriptText.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("TRANSCRIPT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                        val languageName = state.transcriptLanguage?.let { WhisperLanguages.label(it) ?: it.uppercase() } ?: "Language detected"
                        Text("$languageName · Edit before sharing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(state.transcriptText)) }) { Icon(Icons.Rounded.ContentCopy, "Copy transcript") }
                        IconButton(onClick = onSave) { Icon(Icons.Rounded.SaveAlt, "Save as text") }
                        IconButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, state.transcriptText) }
                            context.startActivity(Intent.createChooser(share, "Share transcript"))
                        }) { Icon(Icons.AutoMirrored.Rounded.Send, "Share transcript") }
                    }
                }
            }
            item {
                OutlinedTextField(value = state.transcriptText, onValueChange = onEditTranscript, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 14, shape = RoundedCornerShape(18.dp), label = { Text("Transcript") })
            }
            if (state.transcriptMarks.isNotEmpty()) item {
                Text("TRANSCRIPT TIMESTAMPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
            }
            items(timingGroups.take(100)) { (seconds, marks) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(formatRecordingTime(seconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(marks.joinToString(" ") { it.text }, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { TextButton(onClick = onChooseAudio, modifier = Modifier.fillMaxWidth()) { Text("Choose another recording") } }
            item { OutlinedButton(onClick = onTranscribe, enabled = !state.transcriptionBusy, modifier = Modifier.fillMaxWidth()) { Text("Transcribe again") } }
        }
        if (state.status == "Transcript saved") item { Text("Transcript saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        item { Text("Speech recognition may be slower or unavailable on some phones. Audio format support depends on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Your recording is processed on this phone and isn't uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun formatRecordingTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
