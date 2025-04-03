package com.somendrasaini.internetgifcreator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import kotlin.math.min
import kotlin.math.roundToInt

private const val GIF_START_PERCENT = 0f
private const val GIF_END_PERCENT = 1f
private const val GIF_DURATION_SECONDS = 5f
private const val DEFAULT_FPS = 15
private const val MIN_FPS = 5
private const val MAX_FPS = 30
private const val MIN_DURATION = 1f
private const val MAX_DURATION = 15f
private const val MIN_WIDTH = 240
private const val MAX_WIDTH = 720
private const val DEFAULT_WIDTH = 480

data class StreamInfo(
    val quality: String,
    val itag: String,
    val size: String
)

data class GifSettings(
    val startPercent: Float = GIF_START_PERCENT,
    val endPercent: Float = GIF_END_PERCENT,
    val durationSeconds: Float = GIF_DURATION_SECONDS,
    val fps: Int = DEFAULT_FPS,
    val width: Int = DEFAULT_WIDTH
)

val ProductSans = FontFamily(
    Font(R.font.productsans_regular, FontWeight.Normal),
    Font(R.font.productsans_bold, FontWeight.Bold),
    Font(R.font.productsans_bold_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.productsans_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

private val VidDownTypography = Typography(
    displayLarge = TextStyle(fontFamily = ProductSans),
    displayMedium = TextStyle(fontFamily = ProductSans),
    displaySmall = TextStyle(fontFamily = ProductSans),
    headlineLarge = TextStyle(fontFamily = ProductSans),
    headlineMedium = TextStyle(fontFamily = ProductSans),
    headlineSmall = TextStyle(fontFamily = ProductSans),
    titleLarge = TextStyle(fontFamily = ProductSans, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = ProductSans, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = ProductSans),
    bodyLarge = TextStyle(fontFamily = ProductSans),
    bodyMedium = TextStyle(fontFamily = ProductSans),
    bodySmall = TextStyle(fontFamily = ProductSans),
    labelLarge = TextStyle(fontFamily = ProductSans, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = ProductSans),
    labelSmall = TextStyle(fontFamily = ProductSans)
)

@Composable
fun VidDownTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        typography = VidDownTypography,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissions(this)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            VidDownTheme {
                VideoDownloadApp()
            }
        }
    }

    private fun requestStoragePermissions(activity: Activity) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        ActivityCompat.requestPermissions(activity, permissions, 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadApp() {
    var urlInput by remember { mutableStateOf(TextFieldValue()) }
    var isLoading by remember { mutableStateOf(false) }
    var rawResponse by remember { mutableStateOf("") }
    var videoStreams by remember { mutableStateOf<List<StreamInfo>>(emptyList()) }
    var audioStreams by remember { mutableStateOf<List<StreamInfo>>(emptyList()) }
    var selectedVideoStream by remember { mutableStateOf<StreamInfo?>(null) }
    var selectedAudioStream by remember { mutableStateOf<StreamInfo?>(null) }
    var statusText by remember { mutableStateOf("Enter a YouTube URL to begin") }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var showDebugInfo by remember { mutableStateOf(false) }
    var showGifDialog by remember { mutableStateOf(false) }
    var gifSettings by remember { mutableStateOf(GifSettings()) }
    var isCreatingGif by remember { mutableStateOf(false) }
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var createdGifPath by remember { mutableStateOf<String?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleScope = lifecycleOwner.lifecycleScope
    val scrollState = rememberScrollState()

    fun parseStreams(response: String): Pair<List<StreamInfo>, List<StreamInfo>> {
        val videoPattern = """(\d+p)\s*\(itag=([^,]+),\s*Size=([^)]+)\)""".toRegex()
        val audioPattern = """([\d.]+)\s*kbps\s*\(itag=([^,]+),\s*Size=([^)]+)\)""".toRegex()

        val videos = videoPattern.findAll(response).map {
            StreamInfo(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }.toList()

        val audios = audioPattern.findAll(response).map {
            StreamInfo("${it.groupValues[1]} kbps", it.groupValues[2], it.groupValues[3])
        }.toList()

        return Pair(videos, audios)
    }

    fun fetchStreams(url: String) {
        if (url.isEmpty()) {
            snackbarMessage = "Please enter a URL"
            showSnackbar = true
            return
        }

        isLoading = true
        statusText = "Fetching available streams..."

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Python.getInstance()
                        .getModule("get_streams")
                        .callAttr("get_best_streams", url)
                        .toString()
                }
                rawResponse = result
                val (videos, audios) = parseStreams(result)
                videoStreams = videos
                audioStreams = audios
                statusText = "Found ${videos.size} video and ${audios.size} audio options"
            } catch (e: Exception) {
                snackbarMessage = "Error: ${e.message}"
                showSnackbar = true
                statusText = "Failed to fetch streams"
                Log.e("FetchStreams", e.stackTraceToString())
            } finally {
                isLoading = false
            }
        }
    }

    fun getDownloadDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "VidDown"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVideoDuration(videoPath: String): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.fromFile(File(videoPath)))
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLong()?.div(1000f) ?: 0f
        } catch (e: Exception) {
            Log.e("VideoDuration", "Error getting duration", e)
            0f
        } finally {
            retriever.release()
        }
    }

    fun createVideoThumbnail(videoPath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.fromFile(File(videoPath)))
            retriever.frameAtTime
        } catch (e: Exception) {
            Log.e("VideoThumbnail", "Error creating thumbnail", e)
            null
        } finally {
            retriever.release()
        }
    }

    fun downloadFile(itag: String, isVideo: Boolean, latch: CountDownLatch) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Python.getInstance()
                        .getModule("download_streams")
                        .callAttr(
                            "download_content",
                            itag,
                            urlInput.text,
                            isVideo,
                            getDownloadDir().absolutePath
                        )
                        .toInt()
                }
                if (result != 100) {
                    snackbarMessage = "Download failed for ${if (isVideo) "video" else "audio"}"
                    showSnackbar = true
                }
            } catch (e: Exception) {
                snackbarMessage = "Download error: ${e.message}"
                showSnackbar = true
                Log.e("DownloadFile", e.stackTraceToString())
            } finally {
                latch.countDown()
            }
        }
    }

    fun combineFiles() {
        lifecycleScope.launch {
            try {
                val dir = getDownloadDir()
                val videoFile = dir.listFiles()?.find { it.nameWithoutExtension == "video" }
                val audioFile = dir.listFiles()?.find { it.nameWithoutExtension == "audio" }

                if (videoFile == null || audioFile == null) {
                    snackbarMessage = "Missing video or audio file"
                    showSnackbar = true
                    return@launch
                }

                val outputPath = File(dir, "final_output.mp4").absolutePath
                val cmd = "-y -i ${videoFile.path} -i ${audioFile.path} -c:v copy -c:a aac $outputPath"

                val session = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(cmd)
                }

                if (ReturnCode.isSuccess(session.returnCode)) {
                    videoFile.delete()
                    audioFile.delete()
                    snackbarMessage = "Video created successfully in Downloads/VidDown!"
                    statusText = "Download complete!"

                    videoThumbnail = withContext(Dispatchers.IO) {
                        createVideoThumbnail(outputPath)
                    }
                } else {
                    snackbarMessage = "Error combining files: ${session.failStackTrace}"
                }
                showSnackbar = true
            } catch (e: Exception) {
                snackbarMessage = "Error during combining: ${e.message}"
                showSnackbar = true
                Log.e("CombineFiles", e.stackTraceToString())
            }
        }
    }

    fun createGifFromVideo(videoPath: String, settings: GifSettings) {
        lifecycleScope.launch {
            isCreatingGif = true
            statusText = "Creating GIF..."

            try {
                val outputPath = withContext(Dispatchers.IO) {
                    val dir = getDownloadDir()
                    val outputFile = File(dir, "output_${System.currentTimeMillis()}.gif")
                    outputFile.absolutePath
                }

                val totalDurationSec = getVideoDuration(videoPath)
                val startTimeSec = totalDurationSec * settings.startPercent
                val endTimeSec = totalDurationSec * settings.endPercent
                val selectedDurationSec = endTimeSec - startTimeSec
                val actualDurationSec = min(selectedDurationSec, settings.durationSeconds)

                val cmd = """
                    -ss $startTimeSec -t $actualDurationSec -i "$videoPath"
                    -vf "fps=${settings.fps},scale=${settings.width}:-1:flags=lanczos,split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
                    -loop 0 -r ${settings.fps} "$outputPath"
                """.trimIndent().replace("\n", " ")

                val session = FFmpegKit.execute(cmd)

                if (ReturnCode.isSuccess(session.returnCode)) {
                    withContext(Dispatchers.IO) {
                        File(outputPath).setReadable(true, false)
                    }
                    createdGifPath = outputPath
                    snackbarMessage = "GIF created successfully!"
                    showShareDialog = true
                } else {
                    snackbarMessage = "Failed to create GIF: ${session.failStackTrace}"
                }
                showSnackbar = true
            } catch (e: Exception) {
                snackbarMessage = "Error creating GIF: ${e.message}"
                showSnackbar = true
                Log.e("CreateGif", e.stackTraceToString())
            } finally {
                isCreatingGif = false
            }
        }
    }

    fun shareGif(context: Context, gifPath: String) {
        try {
            val file = File(gifPath)
            if (!file.exists()) {
                throw FileNotFoundException("GIF file not found at $gifPath")
            }

            val providerAuthority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(
                context,
                providerAuthority,
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/gif"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                val resInfoList = context.packageManager
                    .queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share GIF"))
        } catch (e: Exception) {
            Log.e("ShareGIF", "Error sharing GIF", e)
            throw e
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Internet GIF Creator",
                        style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube URL") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Button(
                onClick = { fetchStreams(urlInput.text) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && urlInput.text.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, "Download")
                    Text("Get Available Streams")
                }
            }

            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(statusText)
                }
            } else {
                Text(statusText)
            }

            if (videoStreams.isNotEmpty() || audioStreams.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StreamSelector(
                        label = "Video Quality",
                        icon = Icons.Default.PlayArrow,
                        streams = videoStreams,
                        selectedStream = selectedVideoStream,
                        onStreamSelected = { selectedVideoStream = it }
                    )

                    StreamSelector(
                        label = "Audio Quality",
                        icon = Icons.Default.MusicNote,
                        streams = audioStreams,
                        selectedStream = selectedAudioStream,
                        onStreamSelected = { selectedAudioStream = it }
                    )
                }
            }

            if (selectedVideoStream != null && selectedAudioStream != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            statusText = "Starting download..."
                            lifecycleScope.launch {
                                val latch = CountDownLatch(2)
                                try {
                                    withContext(Dispatchers.IO) {
                                        downloadFile(selectedVideoStream!!.itag, true, latch)
                                        downloadFile(selectedAudioStream!!.itag, false, latch)
                                        latch.await()
                                    }
                                    statusText = "Combining files..."
                                    combineFiles()
                                } catch (e: Exception) {
                                    statusText = "Error: ${e.message}"
                                    snackbarMessage = "Download failed: ${e.message}"
                                    showSnackbar = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, "Download")
                            Text("Download & Combine")
                        }
                    }

                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                try {
                                    val dir = getDownloadDir()
                                    val videoFile = withContext(Dispatchers.IO) {
                                        dir.listFiles()?.firstOrNull { file ->
                                            file.nameWithoutExtension == "final_output" ||
                                                    file.extension.equals("mp4", ignoreCase = true)
                                        }
                                    }

                                    if (videoFile != null) {
                                        if (videoThumbnail == null) {
                                            videoThumbnail = withContext(Dispatchers.IO) {
                                                createVideoThumbnail(videoFile.path)
                                            }
                                        }
                                        showGifDialog = true
                                    } else {
                                        snackbarMessage = "No video file found. Please download first."
                                        showSnackbar = true
                                    }
                                } catch (e: Exception) {
                                    snackbarMessage = "Error: ${e.message}"
                                    showSnackbar = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Gif, "Create GIF")
                            Text("Create GIF")
                        }
                    }
                }
            }

            videoThumbnail?.let { thumbnail ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Video Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(thumbnail.width.toFloat() / thumbnail.height.toFloat())
                    )
                }
            }

            Button(
                onClick = { showDebugInfo = !showDebugInfo },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(if (showDebugInfo) "Hide Debug Info" else "Show Debug Info")
            }

            if (showDebugInfo && rawResponse.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = rawResponse,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showSnackbar) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(
                        onClick = { showSnackbar = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    ) {
                        Text("Dismiss")
                    }
                },
                containerColor = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    text = snackbarMessage,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }

        if (showGifDialog) {
            AlertDialog(
                onDismissRequest = { showGifDialog = false },
                title = { Text("Create GIF from Video") },
                text = {
                    Column {
                        Text("Select video portion:")
                        RangeSlider(
                            value = gifSettings.startPercent..gifSettings.endPercent,
                            onValueChange = { range ->
                                gifSettings = gifSettings.copy(
                                    startPercent = range.start,
                                    endPercent = range.endInclusive
                                )
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                        Text("Duration: ${gifSettings.durationSeconds.roundToInt()} seconds")
                        Slider(
                            value = gifSettings.durationSeconds,
                            onValueChange = { gifSettings = gifSettings.copy(durationSeconds = it) },
                            valueRange = MIN_DURATION..MAX_DURATION,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                        Text("FPS: ${gifSettings.fps}")
                        Slider(
                            value = gifSettings.fps.toFloat(),
                            onValueChange = { gifSettings = gifSettings.copy(fps = it.roundToInt()) },
                            valueRange = MIN_FPS.toFloat()..MAX_FPS.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                        Text("Width: ${gifSettings.width}px")
                        Slider(
                            value = gifSettings.width.toFloat(),
                            onValueChange = { gifSettings = gifSettings.copy(width = it.roundToInt()) },
                            valueRange = MIN_WIDTH.toFloat()..MAX_WIDTH.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val dir = getDownloadDir()
                            val videoFile = dir.listFiles()?.firstOrNull { file ->
                                file.nameWithoutExtension == "final_output" ||
                                        file.extension.equals("mp4", ignoreCase = true)
                            }

                            if (videoFile != null) {
                                createGifFromVideo(videoFile.path, gifSettings)
                                showGifDialog = false
                            } else {
                                snackbarMessage = "Video file not found"
                                showSnackbar = true
                            }
                        },
                        enabled = !isCreatingGif,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isCreatingGif) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Create GIF")
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showGifDialog = false },
                        enabled = !isCreatingGif,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showShareDialog && createdGifPath != null) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text("GIF Created!") },
                text = { Text("Your GIF was successfully created. Would you like to share it?") },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                shareGif(context, createdGifPath!!)
                            } catch (e: Exception) {
                                snackbarMessage = when (e) {
                                    is FileNotFoundException -> "GIF file not found. Please try creating it again."
                                    else -> "Failed to share: ${e.message}"
                                }
                                showSnackbar = true
                            } finally {
                                showShareDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Share")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showShareDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSelector(
    label: String,
    icon: ImageVector,
    streams: List<StreamInfo>,
    selectedStream: StreamInfo?,
    onStreamSelected: (StreamInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(icon, label, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedStream?.let { "${it.quality} (${it.size})" } ?: "Select $label",
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                streams.forEach { stream ->
                    DropdownMenuItem(
                        text = { Text("${stream.quality} (${stream.size})") },
                        onClick = {
                            onStreamSelected(stream)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewVideoDownloadApp() {
    VidDownTheme {
        VideoDownloadApp()
    }
}