package com.example.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Note
import com.example.data.remote.GeminiService
import com.example.data.repository.NoteRepository
import com.example.receiver.ReminderReceiver
import com.example.util.FileHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository: NoteRepository

    // Theme preferences
    private val sharedPrefs = context.getSharedPreferences("smart_notes_prefs", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    // Search and Tags state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag = _selectedTag.asStateFlow()

    // Notes Flow
    val notes: StateFlow<List<Note>>

    // Active screen state (for simple navigation)
    // "LIST", "EDITOR", "CANVAS", "PDF_PREVIEW"
    private val _currentScreen = MutableStateFlow("LIST")
    val currentScreen = _currentScreen.asStateFlow()

    private val _editingNoteId = MutableStateFlow<Int?>(null)
    val editingNoteId = _editingNoteId.asStateFlow()

    // Gemini states
    private val _isSmartLoading = MutableStateFlow(false)
    val isSmartLoading = _isSmartLoading.asStateFlow()

    private val _smartMessage = MutableStateFlow<String?>(null)
    val smartMessage = _smartMessage.asStateFlow()

    // Audio recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordDuration = MutableStateFlow(0L)
    val recordDuration = _recordDuration.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var recordTimerRunnable: Runnable? = null

    // Audio playing state
    private val _playingNoteId = MutableStateFlow<Int?>(null)
    val playingNoteId = _playingNoteId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress = _audioProgress.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressRunnable: Runnable? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())

        // Combine search query and tag selection for filtering
        notes = combine(_searchQuery, _selectedTag, repository.allNotes) { query, tag, allNotesList ->
            var list = allNotesList
            if (query.isNotEmpty()) {
                list = list.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.content.contains(query, ignoreCase = true) ||
                            it.tags.contains(query, ignoreCase = true)
                }
            }
            if (tag != null) {
                list = list.filter { it.tags.split(",").map { t -> t.trim() }.contains(tag) }
            }
            list
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // --- NAVIGATION HELPERS ---

    fun navigateToList() {
        _currentScreen.value = "LIST"
        _editingNoteId.value = null
        stopAudioPlayback()
    }

    fun navigateToEditor(noteId: Int? = null) {
        _editingNoteId.value = noteId
        _currentScreen.value = "EDITOR"
    }

    fun navigateToCanvas() {
        _currentScreen.value = "CANVAS"
    }

    // --- NOTE DB ACTIONS ---

    fun saveNote(note: Note, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
            val id = if (note.id == 0) {
                repository.insertNote(updatedNote).toInt()
            } else {
                repository.updateNote(updatedNote)
                note.id
            }
            onComplete(id)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            // Delete associated files if they exist
            note.imagePath?.let { File(it).delete() }
            note.pdfPath?.let { File(it).delete() }
            note.drawingPath?.let { File(it).delete() }
            note.voicePath?.let { File(it).delete() }
            repository.deleteNote(note)
        }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isFavorite = !note.isFavorite))
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    // --- FILE PICKER ATTACHMENTS ---

    fun attachImage(uri: Uri, note: Note, onComplete: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = FileHelper.copyUriToInternalStorage(context, uri, "images")
            if (path != null) {
                val updatedNote = note.copy(imagePath = path)
                withContext(Dispatchers.Main) {
                    onComplete(updatedNote)
                }
            }
        }
    }

    fun attachPdf(uri: Uri, note: Note, onComplete: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = FileHelper.copyUriToInternalStorage(context, uri, "pdfs")
            if (path != null) {
                val updatedNote = note.copy(pdfPath = path)
                withContext(Dispatchers.Main) {
                    onComplete(updatedNote)
                }
            }
        }
    }

    // --- DRAWING CANVAS ACTIONS ---

    fun saveDrawing(bitmap: Bitmap, note: Note, onComplete: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = FileHelper.saveBitmapToInternalStorage(context, bitmap, "drawings")
            if (path != null) {
                // Delete previous drawing if exists to save space
                note.drawingPath?.let { File(it).delete() }
                val updatedNote = note.copy(drawingPath = path)
                withContext(Dispatchers.Main) {
                    onComplete(updatedNote)
                }
            }
        }
    }

    // --- VOICE RECORDER ---

    fun startVoiceRecording() {
        if (_isRecording.value) return
        try {
            recordingFile = FileHelper.createAudioFile(context)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }
            _isRecording.value = true
            recordStartTime = System.currentTimeMillis()
            _recordDuration.value = 0L

            // Timer
            recordTimerRunnable = object : Runnable {
                override fun run() {
                    _recordDuration.value = System.currentTimeMillis() - recordStartTime
                    handler.postDelayed(this, 100)
                }
            }
            handler.post(recordTimerRunnable!!)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Failed to start voice recording", e)
        }
    }

    fun stopVoiceRecording(note: Note, onComplete: (Note) -> Unit) {
        if (!_isRecording.value) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false

            recordTimerRunnable?.let { handler.removeCallbacks(it) }
            recordTimerRunnable = null

            val duration = System.currentTimeMillis() - recordStartTime
            val voicePath = recordingFile?.absolutePath

            if (voicePath != null) {
                val updatedNote = note.copy(voicePath = voicePath, voiceDuration = duration)
                onComplete(updatedNote)
            }
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Failed to stop voice recording", e)
        }
    }

    // --- VOICE PLAYER ---

    fun toggleAudioPlayback(note: Note) {
        if (_playingNoteId.value == note.id && _isPlaying.value) {
            pauseAudioPlayback()
        } else if (_playingNoteId.value == note.id && !_isPlaying.value) {
            resumeAudioPlayback()
        } else {
            startAudioPlayback(note)
        }
    }

    private fun startAudioPlayback(note: Note) {
        val path = note.voicePath ?: return
        val file = File(path)
        if (!file.exists()) return

        stopAudioPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    stopAudioPlayback()
                }
            }
            _playingNoteId.value = note.id
            _isPlaying.value = true

            progressRunnable = object : Runnable {
                override fun run() {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _audioProgress.value = player.currentPosition.toFloat() / player.duration.toFloat()
                            handler.postDelayed(this, 100)
                        }
                    }
                }
            }
            handler.post(progressRunnable!!)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Failed to start audio playback", e)
        }
    }

    private fun pauseAudioPlayback() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }

    private fun resumeAudioPlayback() {
        mediaPlayer?.start()
        _isPlaying.value = true
        progressRunnable?.let { handler.post(it) }
    }

    private fun stopAudioPlayback() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _playingNoteId.value = null
        _audioProgress.value = 0f
    }

    // --- REMINDERS & ALARMS ---

    fun scheduleReminder(note: Note, timeInMillis: Long) {
        val updatedNote = note.copy(reminderTime = timeInMillis)
        saveNote(updatedNote) { id ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", id)
                putExtra("NOTE_TITLE", note.title)
                putExtra("NOTE_CONTENT", note.content)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
            Log.d("NoteViewModel", "Scheduled reminder at $timeInMillis for note $id")
        }
    }

    fun cancelReminder(note: Note) {
        val updatedNote = note.copy(reminderTime = null)
        saveNote(updatedNote) { id ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("NoteViewModel", "Cancelled reminder for note $id")
        }
    }

    // --- EXPORT TO PDF ---

    fun exportToPdf(note: Note, onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileHelper.exportNoteToPdf(context, note)
            withContext(Dispatchers.Main) {
                onResult(file)
            }
        }
    }

    // --- BACKUP & RESTORE ---

    fun exportBackup(onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read all notes in background
                val database = AppDatabase.getDatabase(context)
                val allNotesList = database.noteDao().getAllNotes()
                
                // Collect first value from Flow synchronously in the IO thread
                var notesList = emptyList<Note>()
                val job = viewModelScope.launch {
                    allNotesList.collect { list ->
                        notesList = list
                    }
                }
                // Wait briefly for flow emission
                Thread.sleep(200)
                job.cancel()

                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(List::class.java, Note::class.java)
                val adapter = moshi.adapter<List<Note>>(type)
                val jsonString = adapter.toJson(notesList)

                val backupFolder = File(context.filesDir, "backups")
                if (!backupFolder.exists()) backupFolder.mkdirs()

                val backupFile = File(backupFolder, "backup_smartnotes_${System.currentTimeMillis()}.json")
                val outputStream = FileOutputStream(backupFile)
                outputStream.write(jsonString.toByteArray())
                outputStream.flush()
                outputStream.close()

                withContext(Dispatchers.Main) {
                    onResult(backupFile)
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to export backup", e)
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(List::class.java, Note::class.java)
                val adapter = moshi.adapter<List<Note>>(type)
                val notesList = adapter.fromJson(jsonString)

                if (notesList != null) {
                    val database = AppDatabase.getDatabase(context)
                    for (note in notesList) {
                        // Insert imported note as a new note (reset ID to 0) or overwrite
                        database.noteDao().insertNote(note.copy(id = 0, updatedAt = System.currentTimeMillis()))
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to import backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun exportSqliteDbBackup(onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure any pending transactions are written
                val database = AppDatabase.getDatabase(context)
                try {
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);").close()
                } catch (e: Exception) {
                    Log.e("NoteViewModel", "WAL checkpoint failed, proceeding anyway", e)
                }

                val dbFile = context.getDatabasePath("smart_notes_database")
                if (dbFile.exists()) {
                    val backupFolder = File(context.filesDir, "backups_sqlite")
                    if (!backupFolder.exists()) backupFolder.mkdirs()

                    val backupFile = File(backupFolder, "smart_notes_database.db")
                    dbFile.inputStream().use { input ->
                        backupFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onResult(backupFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to export SQLite backup", e)
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun importSqliteDbBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val dbFile = context.getDatabasePath("smart_notes_database")

                // Close DB first to unlock files
                AppDatabase.getDatabase(context).close()

                // Copy the input stream directly to the dbFile
                dbFile.outputStream().use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }

                // Re-open/Initialize db to ensure it's loaded
                AppDatabase.getDatabase(context)

                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to import SQLite backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    // --- GEMINI ACTIONS ---

    fun triggerAutoTagging(note: Note, onComplete: (String) -> Unit) {
        if (note.title.isBlank() && note.content.isBlank()) return
        _isSmartLoading.value = true
        _smartMessage.value = "جاري توليد وسوم ذكية باستخدام الذكاء الاصطناعي..."
        viewModelScope.launch {
            val tags = GeminiService.autoTagNote(note.title, note.content)
            _isSmartLoading.value = false
            if (tags.isNotEmpty()) {
                val tagsString = tags.joinToString(", ")
                _smartMessage.value = "تم توليد الوسوم بنجاح!"
                onComplete(tagsString)
            } else {
                _smartMessage.value = "فشل في توليد وسوم للملاحظة."
            }
        }
    }

    fun triggerSummarization(note: Note, onComplete: (String) -> Unit) {
        if (note.content.isBlank()) return
        _isSmartLoading.value = true
        _smartMessage.value = "جاري تلخيص الملاحظة عبر الذكاء الاصطناعي..."
        viewModelScope.launch {
            val summary = GeminiService.summarizeNote(note.title, note.content)
            _isSmartLoading.value = false
            _smartMessage.value = "تم التلخيص!"
            onComplete(summary)
        }
    }

    fun triggerImageAnalysis(note: Note, prompt: String, onComplete: (String) -> Unit) {
        val path = note.imagePath ?: return
        val file = File(path)
        if (!file.exists()) return

        _isSmartLoading.value = true
        _smartMessage.value = "جاري تحليل الصورة بالذكاء الاصطناعي..."
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val description = GeminiService.analyzeImage(bitmap, prompt)
                withContext(Dispatchers.Main) {
                    _isSmartLoading.value = false
                    _smartMessage.value = "اكتمل تحليل الصورة!"
                    onComplete(description)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _isSmartLoading.value = false
                    _smartMessage.value = "فشل في قراءة ملف الصورة."
                }
            }
        }
    }

    fun clearSmartMessage() {
        _smartMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
    }
}
