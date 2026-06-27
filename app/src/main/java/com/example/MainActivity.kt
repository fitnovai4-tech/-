package com.example

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import com.example.data.model.Note
import com.example.ui.screens.DrawingCanvasScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NoteListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoteViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: NoteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val editingNoteId by viewModel.editingNoteId.collectAsState()
                val notes by viewModel.notes.collectAsState()

                // Request runtime permissions on launch
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val recordGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
                    if (!recordGranted) {
                        Toast.makeText(
                            this,
                            "ملاحظة: الإذن بالوصول للميكروفون مطلوب لتسجيل الملاحظات الصوتية.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                // Handle intent extras from reminders
                LaunchedEffect(intent) {
                    val noteIdFromNotification = intent.getIntExtra("NOTE_ID", 0)
                    if (noteIdFromNotification != 0) {
                        viewModel.navigateToEditor(noteIdFromNotification)
                    }
                }

                // Force RTL LayoutDirection for beautiful Arabic UI flow
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Crossfade(
                                targetState = currentScreen,
                                label = "screen_transition",
                                modifier = Modifier.padding(innerPadding)
                            ) { screen ->
                                when (screen) {
                                    "LIST" -> {
                                        NoteListScreen(
                                            viewModel = viewModel,
                                            onNoteClick = { id ->
                                                viewModel.navigateToEditor(id)
                                            },
                                            onAddNote = {
                                                viewModel.navigateToEditor(null)
                                            }
                                        )
                                    }
                                    "EDITOR" -> {
                                        NoteEditorScreen(
                                            viewModel = viewModel,
                                            noteId = editingNoteId,
                                            onBack = {
                                                viewModel.navigateToList()
                                            }
                                        )
                                    }
                                    "CANVAS" -> {
                                        val activeNote = remember(editingNoteId, notes) {
                                            notes.find { it.id == editingNoteId } ?: Note(title = "", content = "")
                                        }
                                        DrawingCanvasScreen(
                                            viewModel = viewModel,
                                            note = activeNote,
                                            onSave = { updatedNote ->
                                                // Go back to editor
                                                viewModel.navigateToEditor(updatedNote.id)
                                            },
                                            onBack = {
                                                viewModel.navigateToEditor(editingNoteId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
