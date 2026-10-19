package app.ytfilter.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Face detection (ML Kit) + face embedding (MobileFaceNet on LiteRT), fully on-device.
 * Drop `mobilefacenet.tflite` into app/src/main/assets; without it [isAvailable] is false.
 */
class FaceEmbedder(context: Context) {
    private val interpreter: Interpreter? = runCatching {
        context.assets.openFd(MODEL_FILE).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { ch ->
                Interpreter(ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength))
            }
        }
    }.getOrNull()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f) // thumbnails often show small faces
            .build()
    )

    // Interpreter is not thread-safe.
    private val lock = Mutex()

    val isAvailable: Boolean get() = interpreter != null

    /** One embedding per detected face, largest face first. */
    suspend fun embedFaces(bitmap: Bitmap): List<FloatArray> {
        val model = interpreter ?: return emptyList()
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
        return withContext(Dispatchers.Default) {
            faces.mapNotNull { face -> crop(bitmap, face.boundingBox)?.let { lock.withLock { run(model, it) } } }
        }
    }

    private fun crop(src: Bitmap, box: Rect): Bitmap? {
        // Pad the box a little: embedding models are trained on slightly loose crops.
        val pad = (box.width() * 0.1f).toInt()
        val r = Rect(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
        if (!r.intersect(0, 0, src.width, src.height) || r.width() < 16 || r.height() < 16) return null
        return Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
    }

    private fun run(model: Interpreter, face: Bitmap): FloatArray {
        val inShape = model.getInputTensor(0).shape() // [1, H, W, 3]
        val h = inShape[1]
        val w = inShape[2]
        val scaled = Bitmap.createScaledBitmap(face, w, h, true)
        val pixels = IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
        val input = ByteBuffer.allocateDirect(4 * w * h * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        input.rewind()
        val dim = model.getOutputTensor(0).shape().last()
        val output = arrayOf(FloatArray(dim))
        model.run(input, output)
        return output[0]
    }

    companion object {
        const val MODEL_FILE = "mobilefacenet.tflite"
    }
}
