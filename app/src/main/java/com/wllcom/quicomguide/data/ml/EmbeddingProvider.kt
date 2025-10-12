package com.wllcom.quicomguide.data.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class EmbeddingProvider @Inject constructor(@ApplicationContext private val ctx: Context,
                                            private val tfliteName: String = "intfloat_multilingual-e5-small.tflite",
                                            private val tokenizerName: String = "tokenizer.json",
                                            private val maxLen: Int = 512) {

    private lateinit var interpreter: Interpreter
    private lateinit var tokenizer: Tokenizer
    var publicTokenizer: Tokenizer? = null

    private val _isReady = MutableStateFlow(false)
    val isReadyFlow: StateFlow<Boolean> = _isReady.asStateFlow()

    private val embedMutex = Mutex()
    private val initMutex = Mutex()


    private lateinit var modelPath: String
    private lateinit var tokenizerPath: String
    fun setPaths(modelPath: String, tokenizerPath: String) {
        this.modelPath = modelPath
        this.tokenizerPath = tokenizerPath
    }



    suspend fun ensureReady() = initMutex.withLock {
        if (!_isReady.value) {
            initModel()
            initTokenizer()
            _isReady.value = true
        }
    }

    val isReady: Boolean
        get() = _isReady.value

    fun warmUp() {
        CoroutineScope(Dispatchers.Default).launch {
            ensureReady()
        }
    }

    private fun initModel() {
//        val modelBuffer = loadModelFileFromAssets(tfliteName)
        val modelBuffer = loadModelFileFromPath(modelPath)
        interpreter = Interpreter(modelBuffer)

        checkInputs()
        checkOutputs()

        interpreter.apply {
            resizeInput(0, intArrayOf(1, maxLen))
            resizeInput(1, intArrayOf(1, maxLen))
            resizeInput(2, intArrayOf(1, maxLen))
            allocateTensors()
        }
    }

    private fun initTokenizer() {
//        tokenizer = Tokenizer(ctx, tokenizerName)
        tokenizer = Tokenizer(ctx, tokenizerPath)
        publicTokenizer = tokenizer
    }

    suspend fun embed(text: String): FloatArray = embedMutex.withLock {
        ensureReady()

        val tk = tokenizer
        val model = interpreter

        val inputIds = tk.encode(text)
        val realLen = inputIds.size
        val padId = tk.tokenToId["<pad>"] ?: 1

        val ids = IntArray(maxLen) { i -> if (i < realLen) inputIds[i] else padId }
        val mask = IntArray(maxLen) { i -> if (i < realLen) 1 else 0 }
        val tokenTypes = IntArray(maxLen) { 0 }

        val inputs = arrayOf(
            arrayOf(mask),
            arrayOf(ids),
            arrayOf(tokenTypes)
        )

        val outputTensor = model.getOutputTensor(0)
        val shape = outputTensor.shape()
        val output = when (shape.size) {
            2 -> Array(1) { FloatArray(shape[1]) } // [1, hidden_dim]
            3 -> Array(1) { Array(shape[1]) { FloatArray(shape[2]) } } // [1, seq_len, hidden_dim]
            else -> throw IllegalStateException("Unexpected output shape: ${shape.contentToString()}")
        }

        val outputs = mutableMapOf<Int, Any>(0 to output)
        model.runForMultipleInputsOutputs(inputs, outputs)

        val first = output[0]
        val clsEmbedding = if (first is Array<*>) {
            first[0] as FloatArray
        } else {
            first as FloatArray
        }

        return l2Normalize(clsEmbedding)
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vec) sum += (v * v)
        val norm = sqrt(sum).toFloat().coerceAtLeast(1e-12f)
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }

    private fun loadModelFileFromPath(path: String): MappedByteBuffer {
        val file = File(path)
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    private fun loadModelFileFromAssets(assetName: String): MappedByteBuffer {
        val fileDescriptor = ctx.assets.openFd(assetName)
        FileInputStream(fileDescriptor.fileDescriptor).use { fis ->
            val channel = fis.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    private fun checkInputs(){
        for (i in 0 until interpreter.inputTensorCount) {
            Log.d("INPUT", "${i}: ${interpreter.getInputTensor(i).name()}")
        }
    }

    private fun checkOutputs(){
        for (i in 0 until interpreter.outputTensorCount) {
            Log.d("OUTPUT", "${i}: ${interpreter.getOutputTensor(i).name()}")
        }
    }

    fun close() {
        interpreter.close()
    }
}
