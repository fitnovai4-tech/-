package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.Note
import com.example.ui.viewmodel.NoteViewModel
import com.example.util.FileHelper
import java.io.File

// Drawing Path Data Class
data class StrokePath(
    val path: Path,
    val color: Color,
    val width: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingCanvasScreen(
    viewModel: NoteViewModel,
    note: Note,
    onSave: (Note) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Canvas options state
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    
    // Paths list
    val paths = remember { mutableStateListOf<StrokePath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    
    // Background bitmap (from PDF or attached image)
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Load background bitmap
    LaunchedEffect(key1 = note) {
        if (note.pdfPath != null) {
            // Render the first page of PDF
            val bitmap = FileHelper.renderPdfPageToBitmap(context, note.pdfPath)
            if (bitmap != null) {
                bgBitmap = bitmap
            }
        } else if (note.imagePath != null) {
            val file = File(note.imagePath)
            if (file.exists()) {
                bgBitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة الرسم والتعليق والمستندات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    // Reset drawing
                    IconButton(onClick = { paths.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "مسح اللوحة", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    // Save drawing
                    IconButton(
                        onClick = {
                            // Generate final combined image: Background + Drawing Paths
                            val width = bgBitmap?.width ?: 1080
                            val height = bgBitmap?.height ?: 1920
                            
                            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = AndroidCanvas(resultBitmap)
                            
                            // 1. Draw Background
                            if (bgBitmap != null) {
                                canvas.drawBitmap(bgBitmap!!, 0f, 0f, null)
                            } else {
                                canvas.drawColor(android.graphics.Color.WHITE)
                            }
                            
                            // 2. Draw Paths
                            val paint = AndroidPaint().apply {
                                isAntiAlias = true
                                style = AndroidPaint.Style.STROKE
                                strokeCap = AndroidPaint.Cap.ROUND
                                strokeJoin = AndroidPaint.Join.ROUND
                            }
                            
                            // Scale drawing paths to match background bitmap dimensions
                            // We will scale down the Canvas drawing based on display size
                            paths.forEach { strokePath ->
                                paint.color = android.graphics.Color.argb(
                                    (strokePath.color.alpha * 255).toInt(),
                                    (strokePath.color.red * 255).toInt(),
                                    (strokePath.color.green * 255).toInt(),
                                    (strokePath.color.blue * 255).toInt()
                                )
                                paint.strokeWidth = strokePath.width * (width.toFloat() / 1080f) // proportional stroke
                                canvas.drawPath(strokePath.path.asAndroidPath(), paint)
                            }
                            
                            viewModel.saveDrawing(resultBitmap, note) { updatedNote ->
                                onSave(updatedNote)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "حفظ", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Colors and Stroke Width Selection Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color choices
                val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow)
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedColor == color) 3.dp else 1.dp,
                                color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = color }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stroke Width Slider
                Column(modifier = Modifier.weight(1f)) {
                    Text("حجم القلم: ${strokeWidth.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 2f..30f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Annotation Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val path = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath = path
                                    paths.add(StrokePath(path, selectedColor, strokeWidth))
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    // Trigger recomposition
                                    val temp = currentPath
                                    currentPath = null
                                    currentPath = temp
                                },
                                onDragEnd = {
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    // Draw Background image/PDF first
                    bgBitmap?.let { bitmap ->
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }

                    // Draw all recorded paths
                    paths.forEach { strokePath ->
                        drawPath(
                            path = strokePath.path,
                            color = strokePath.color,
                            style = Stroke(
                                width = strokePath.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
