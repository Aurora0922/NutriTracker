package com.example.nutritracker.feature.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    val currentOnDetected by rememberUpdatedState(
        onBarcodeDetected
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "扫描食品条形码",
                        style = MaterialTheme.typography.titleLarge
                    )

                    TextButton(onClick = onClose) {
                        Text("关闭")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!hasPermission) {
                    Text("请允许使用摄像头")

                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                Manifest.permission.CAMERA
                            )
                        }
                    ) {
                        Text("授权摄像头")
                    }
                } else {
                    val previewView = remember {
                        PreviewView(context)
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    DisposableEffect(lifecycleOwner) {
                        val executor =
                            ContextCompat.getMainExecutor(context)

                        val cameraFuture =
                            ProcessCameraProvider.getInstance(context)

                        val analyzer = BarcodeAnalyzer { code ->
                            currentOnDetected(code)
                        }

                        val preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(
                                    previewView.surfaceProvider
                                )
                            }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                        analysis.setAnalyzer(executor, analyzer)

                        var disposed = false
                        var provider: ProcessCameraProvider? = null

                        cameraFuture.addListener({
                            if (!disposed) {
                                try {
                                    provider = cameraFuture.get()

                                    provider?.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (e: Exception) {
                                    error = e.message ?: "摄像头启动失败"
                                }
                            }
                        }, executor)

                        onDispose {
                            disposed = true
                            analysis.clearAnalyzer()
                            provider?.unbind(preview, analysis)
                            analyzer.close()
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        error ?: "请将食品包装上的条形码对准摄像头"
                    )
                }
            }
        }
    }
}
