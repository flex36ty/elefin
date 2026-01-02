package com.flex.elefin.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Custom GL Surface View for video rendering with post-processing effects.
 * 
 * This view creates an OpenGL pipeline that intercepts ExoPlayer's decoded frames
 * and applies custom shaders for effects like fake HDR, sharpening, and frame blending.
 * 
 * Pipeline: ExoPlayer → MediaCodec → Surface → SurfaceTexture → OES Texture → Shaders → Screen
 * 
 * Frame Blending: Stores the previous frame in an FBO and blends it with the current frame
 * to create a "soap opera" smoothing effect.
 */
class GLVideoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private var surfaceTexture: SurfaceTexture? = null
    private var codecSurface: Surface? = null
    
    private var oesTextureId: Int = 0
    private var frameAvailable = false
    
    private var shaderProgram: Int = 0
    private var vertexBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer
    
    private val transformMatrix = FloatArray(16)
    
    // Callback for when the surface is ready
    private var onSurfaceReadyCallback: ((Surface) -> Unit)? = null
    
    // Video dimensions for aspect ratio correction
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var aspectRatioVertexBuffer: FloatBuffer? = null
    
    /**
     * Set a callback to be notified when the GL surface is ready.
     * This is needed because surface creation is asynchronous.
     */
    fun setOnSurfaceReadyListener(callback: (Surface) -> Unit) {
        onSurfaceReadyCallback = callback
        // If surface is already ready, call immediately
        codecSurface?.let { callback(it) }
    }
    
    /**
     * Set the video dimensions for proper aspect ratio rendering.
     * Call this when the video format is known (e.g., from ExoPlayer's onVideoSizeChanged).
     */
    fun setVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && (width != videoWidth || height != videoHeight)) {
            videoWidth = width
            videoHeight = height
            Log.d(TAG, "🎬 Video size set: ${width}x${height}")
            updateAspectRatioVertexBuffer()
            requestRender()
        }
    }
    
    /**
     * Calculate and update vertex coordinates to maintain proper aspect ratio.
     * This creates letterboxing (black bars top/bottom) or pillarboxing (black bars left/right)
     * as needed to display the video without stretching.
     */
    private fun updateAspectRatioVertexBuffer() {
        if (videoWidth <= 0 || videoHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            aspectRatioVertexBuffer = null
            return
        }
        
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewportAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        
        val scaleX: Float
        val scaleY: Float
        
        if (videoAspect > viewportAspect) {
            // Video is wider than viewport - letterbox (black bars top/bottom)
            scaleX = 1.0f
            scaleY = viewportAspect / videoAspect
        } else {
            // Video is taller than viewport - pillarbox (black bars left/right)
            scaleX = videoAspect / viewportAspect
            scaleY = 1.0f
        }
        
        Log.d(TAG, "🎬 Aspect ratio: video=$videoAspect, viewport=$viewportAspect, scale=($scaleX, $scaleY)")
        
        // Adjusted vertex coordinates for proper aspect ratio
        val adjustedCoords = floatArrayOf(
            -scaleX, -scaleY,  // Bottom-left
             scaleX, -scaleY,  // Bottom-right
            -scaleX,  scaleY,  // Top-left
             scaleX,  scaleY   // Top-right
        )
        
        aspectRatioVertexBuffer = ByteBuffer.allocateDirect(adjustedCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(adjustedCoords)
                position(0)
            }
    }
    
    // Frame blending resources
    private var prevFrameFBO: Int = 0
    private var prevFrameTexture: Int = 0
    private var copyShaderProgram: Int = 0
    private var textureCopyShaderProgram: Int = 0  // For texture-to-texture copy
    private var viewportWidth: Int = 1920
    private var viewportHeight: Int = 1080
    private var hasPreviousFrame: Boolean = false
    private var frameCount: Long = 0
    
    // Effect settings
    var enableFakeHDR: Boolean = false
        set(value) {
            field = value
            requestRender()
        }
    
    var enableSharpening: Boolean = false
        set(value) {
            field = value
            requestRender()
        }
    
    var sharpeningStrength: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            requestRender()
        }
    
    var hdrStrength: Float = 1.3f
        set(value) {
            field = value.coerceIn(1.0f, 2.0f)
            requestRender()
        }
    
    // Frame blending (soap opera effect)
    var enableFrameBlending: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "🎬 Frame blending ${if (value) "ENABLED" else "DISABLED"}")
            requestRender()
        }
    
    var frameBlendStrength: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            Log.d(TAG, "🎬 Frame blend strength: $field")
            requestRender()
        }
    
    // Denoise effect (removes compression noise and mosquito artifacts)
    var enableDenoise: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "🎬 Denoise ${if (value) "ENABLED" else "DISABLED"}")
            requestRender()
        }
    
    var denoiseStrength: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            Log.d(TAG, "🎬 Denoise strength: $field")
            requestRender()
        }
    
    // Deband effect (removes color banding and film grain)
    var enableDeband: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "🎬 Deband ${if (value) "ENABLED" else "DISABLED"}")
            requestRender()
        }
    
    var debandStrength: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            Log.d(TAG, "🎬 Deband strength: $field")
            requestRender()
        }
    
    // FXAA anti-aliasing (reduces jagged edges)
    var enableFXAA: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "🎬 FXAA ${if (value) "ENABLED" else "DISABLED"}")
            requestRender()
        }
    
    // Color grading
    var brightness: Float = 0.0f
        set(value) {
            field = value.coerceIn(-0.5f, 0.5f)
            requestRender()
        }
    
    var contrast: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            requestRender()
        }
    
    var saturation: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2.0f)
            requestRender()
        }
    
    var colorTemperature: Float = 0.0f  // -1.0 = cool (blue), 1.0 = warm (orange)
        set(value) {
            field = value.coerceIn(-1f, 1f)
            requestRender()
        }
    
    // Vertex coordinates for the quad (full screen)
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,  // Bottom-left
         1.0f, -1.0f,  // Bottom-right
        -1.0f,  1.0f,  // Top-left
         1.0f,  1.0f   // Top-right
    )
    
    // Texture coordinates (standard - let SurfaceTexture transform matrix handle orientation)
    private val textureCoords = floatArrayOf(
        0.0f, 0.0f,  // Bottom-left
        1.0f, 0.0f,  // Bottom-right
        0.0f, 1.0f,  // Top-left
        1.0f, 1.0f   // Top-right
    )
    
    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        
        // Initialize buffers
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexCoords)
                position(0)
            }
        
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureCoords)
                position(0)
            }
        
        Log.d(TAG, "GLVideoSurfaceView initialized")
    }
    
    /**
     * Returns the Surface that should be passed to ExoPlayer.
     * ExoPlayer will decode video frames into this surface.
     */
    fun getCodecSurface(): Surface? = codecSurface
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")
        
        // Set clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // Create the OES texture for video frames
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        
        // Create SurfaceTexture that receives MediaCodec frames
        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                frameAvailable = true
                requestRender()
            }
        }
        
        // Create the Surface given to ExoPlayer
        codecSurface = Surface(surfaceTexture)
        
        // Notify callback that surface is ready (on main thread)
        codecSurface?.let { surface ->
            post {
                Log.d(TAG, "🎬 GL Surface ready, notifying callback")
                onSurfaceReadyCallback?.invoke(surface)
            }
        }
        
        // Initialize shader programs
        shaderProgram = createShaderProgram()
        copyShaderProgram = createCopyShaderProgram()
        textureCopyShaderProgram = createTextureCopyShaderProgram()
        
        // Create FBOs for storing previous frames (for frame blending and motion interpolation)
        createPreviousFrameFBO()
        
        Log.d(TAG, "GL pipeline initialized, Surface created")
    }
    
    private fun createPreviousFrameFBO() {
        // Generate FBO for previous frame (used for frame blending)
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        prevFrameFBO = fbos[0]
        
        // Generate texture for previous frame
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        prevFrameTexture = textures[0]
        
        // Setup previous frame texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prevFrameTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            viewportWidth, viewportHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        
        // Attach texture to FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFrameFBO)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, prevFrameTexture, 0
        )
        
        // Check FBO status
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO creation failed: $status")
        } else {
            Log.d(TAG, "Previous frame FBO created: ${viewportWidth}x${viewportHeight}")
        }
        
        // Unbind
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: ${width}x${height}")
        GLES20.glViewport(0, 0, width, height)
        
        // Update viewport dimensions and recreate FBOs if size changed
        if (width != viewportWidth || height != viewportHeight) {
            viewportWidth = width
            viewportHeight = height
            hasPreviousFrame = false
            frameCount = 0
            
            // Recreate FBO with new size
            if (prevFrameFBO != 0) {
                val fbos = intArrayOf(prevFrameFBO)
                GLES20.glDeleteFramebuffers(1, fbos, 0)
                val textures = intArrayOf(prevFrameTexture)
                GLES20.glDeleteTextures(1, textures, 0)
            }
            createPreviousFrameFBO()
            
            // Recalculate aspect ratio vertex buffer
            updateAspectRatioVertexBuffer()
        }
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Update texture with new frame if available
        if (frameAvailable) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(transformMatrix)
            frameAvailable = false
            frameCount++
        }
        
        // Clear the screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        // Draw the video frame with effects
        drawVideoFrame()
        
        // If frame blending is enabled, copy current frame to FBO
        if (enableFrameBlending) {
            copyCurrentFrameToFBO()
            if (!hasPreviousFrame) {
                Log.d(TAG, "🎬 Frame blending active - capturing first frame")
            }
            hasPreviousFrame = true
        }
    }
    
    private fun copyCurrentFrameToFBO() {
        // Bind FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFrameFBO)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        
        // Use copy shader to render current OES texture to FBO
        GLES20.glUseProgram(copyShaderProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(copyShaderProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(copyShaderProgram, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(copyShaderProgram, "uTexture")
        val transformHandle = GLES20.glGetUniformLocation(copyShaderProgram, "uTransform")
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformMatrix, 0)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        
        // Unbind FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }
    
    private fun drawVideoFrame() {
        GLES20.glUseProgram(shaderProgram)
        
        // Get shader attribute/uniform locations
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        val transformHandle = GLES20.glGetUniformLocation(shaderProgram, "uTransform")
        val enableHDRHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableHDR")
        val enableSharpenHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableSharpen")
        val hdrStrengthHandle = GLES20.glGetUniformLocation(shaderProgram, "uHDRStrength")
        val sharpenStrengthHandle = GLES20.glGetUniformLocation(shaderProgram, "uSharpenStrength")
        val texelSizeHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexelSize")
        val enableBlendHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableBlend")
        val blendStrengthHandle = GLES20.glGetUniformLocation(shaderProgram, "uBlendStrength")
        val prevTextureHandle = GLES20.glGetUniformLocation(shaderProgram, "uPrevTexture")
        val hasPrevFrameHandle = GLES20.glGetUniformLocation(shaderProgram, "uHasPrevFrame")
        
        // New effect uniforms
        val enableDenoiseHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableDenoise")
        val denoiseStrengthHandle = GLES20.glGetUniformLocation(shaderProgram, "uDenoiseStrength")
        val enableDebandHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableDeband")
        val debandStrengthHandle = GLES20.glGetUniformLocation(shaderProgram, "uDebandStrength")
        val enableFXAAHandle = GLES20.glGetUniformLocation(shaderProgram, "uEnableFXAA")
        val brightnessHandle = GLES20.glGetUniformLocation(shaderProgram, "uBrightness")
        val contrastHandle = GLES20.glGetUniformLocation(shaderProgram, "uContrast")
        val saturationHandle = GLES20.glGetUniformLocation(shaderProgram, "uSaturation")
        val colorTempHandle = GLES20.glGetUniformLocation(shaderProgram, "uColorTemperature")
        
        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        
        // Set vertex positions - use aspect ratio corrected buffer if available
        val vBuffer = aspectRatioVertexBuffer ?: vertexBuffer
        GLES20.glVertexAttribPointer(
            positionHandle, 2, GLES20.GL_FLOAT, false, 0, vBuffer
        )
        
        // Set texture coordinates
        GLES20.glVertexAttribPointer(
            texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer
        )
        
        // Bind the OES texture (current frame) to texture unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(textureHandle, 0)
        
        // Bind previous frame texture to texture unit 1 (for frame blending)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prevFrameTexture)
        GLES20.glUniform1i(prevTextureHandle, 1)
        
        // Set transform matrix
        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformMatrix, 0)
        
        // Set effect uniforms
        GLES20.glUniform1i(enableHDRHandle, if (enableFakeHDR) 1 else 0)
        GLES20.glUniform1i(enableSharpenHandle, if (enableSharpening) 1 else 0)
        GLES20.glUniform1f(hdrStrengthHandle, hdrStrength)
        GLES20.glUniform1f(sharpenStrengthHandle, sharpeningStrength)
        GLES20.glUniform2f(texelSizeHandle, 1.0f / width.toFloat(), 1.0f / height.toFloat())
        
        // Set frame blending uniforms
        GLES20.glUniform1i(enableBlendHandle, if (enableFrameBlending) 1 else 0)
        GLES20.glUniform1f(blendStrengthHandle, frameBlendStrength)
        GLES20.glUniform1i(hasPrevFrameHandle, if (hasPreviousFrame) 1 else 0)
        
        // Set new effect uniforms
        GLES20.glUniform1i(enableDenoiseHandle, if (enableDenoise) 1 else 0)
        GLES20.glUniform1f(denoiseStrengthHandle, denoiseStrength)
        GLES20.glUniform1i(enableDebandHandle, if (enableDeband) 1 else 0)
        GLES20.glUniform1f(debandStrengthHandle, debandStrength)
        GLES20.glUniform1i(enableFXAAHandle, if (enableFXAA) 1 else 0)
        GLES20.glUniform1f(brightnessHandle, brightness)
        GLES20.glUniform1f(contrastHandle, contrast)
        GLES20.glUniform1f(saturationHandle, saturation)
        GLES20.glUniform1f(colorTempHandle, colorTemperature)
        
        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
    
    private fun createShaderProgram(): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            
            // Check link status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Error linking shader program: $error")
            }
            
            Log.d(TAG, "Shader program created successfully")
        }
    }
    
    private fun createCopyShaderProgram(): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, COPY_FRAGMENT_SHADER)
        
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Error linking copy shader program: $error")
            }
            
            Log.d(TAG, "Copy shader program created successfully")
        }
    }
    
    private fun createTextureCopyShaderProgram(): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, TEXTURE_COPY_FRAGMENT_SHADER)
        
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Error linking texture copy shader program: $error")
            }
            
            Log.d(TAG, "Texture copy shader program created successfully")
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            // Check compilation status
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Error compiling shader: $error")
            }
        }
    }
    
    /**
     * Clean up GL resources
     */
    fun release() {
        // Clear callback to prevent memory leaks or late invocations
        onSurfaceReadyCallback = null
        
        queueEvent {
            codecSurface?.release()
            codecSurface = null
            
            surfaceTexture?.release()
            surfaceTexture = null
            
            if (shaderProgram != 0) {
                GLES20.glDeleteProgram(shaderProgram)
                shaderProgram = 0
            }
            
            if (copyShaderProgram != 0) {
                GLES20.glDeleteProgram(copyShaderProgram)
                copyShaderProgram = 0
            }
            
            if (textureCopyShaderProgram != 0) {
                GLES20.glDeleteProgram(textureCopyShaderProgram)
                textureCopyShaderProgram = 0
            }
            
            if (oesTextureId != 0) {
                val textures = intArrayOf(oesTextureId)
                GLES20.glDeleteTextures(1, textures, 0)
                oesTextureId = 0
            }
            
            if (prevFrameFBO != 0) {
                val fbos = intArrayOf(prevFrameFBO)
                GLES20.glDeleteFramebuffers(1, fbos, 0)
                prevFrameFBO = 0
            }
            
            if (prevFrameTexture != 0) {
                val textures = intArrayOf(prevFrameTexture)
                GLES20.glDeleteTextures(1, textures, 0)
                prevFrameTexture = 0
            }
            
            hasPreviousFrame = false
            frameCount = 0
            
            Log.d(TAG, "GL resources released")
        }
    }
    
    companion object {
        private const val TAG = "GLVideoSurfaceView"
        
        // Simple vertex shader - just passes through positions and texture coordinates
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTransform;
            varying vec2 vTexCoord;
            
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTransform * aTexCoord).xy;
            }
        """
        
        // Fragment shader with all video enhancement effects
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            
            uniform samplerExternalOES uTexture;
            uniform sampler2D uPrevTexture;
            uniform int uEnableHDR;
            uniform int uEnableSharpen;
            uniform int uEnableBlend;
            uniform int uHasPrevFrame;
            uniform float uHDRStrength;
            uniform float uSharpenStrength;
            uniform float uBlendStrength;
            uniform vec2 uTexelSize;
            
            // New effect uniforms
            uniform int uEnableDenoise;
            uniform float uDenoiseStrength;
            uniform int uEnableDeband;
            uniform float uDebandStrength;
            uniform int uEnableFXAA;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uColorTemperature;
            
            varying vec2 vTexCoord;
            
            // Helper: Calculate luminance
            float getLuma(vec3 color) {
                return dot(color, vec3(0.299, 0.587, 0.114));
            }
            
            // Helper: Random function for deband dithering
            float rand(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }
            
            void main() {
                vec3 color = texture2D(uTexture, vTexCoord).rgb;
                
                // ============================================
                // FXAA Anti-aliasing (applied first for best results)
                // ============================================
                if (uEnableFXAA == 1) {
                    // Sample neighboring pixels
                    vec3 rgbNW = texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;
                    vec3 rgbNE = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, -uTexelSize.y)).rgb;
                    vec3 rgbSW = texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x, uTexelSize.y)).rgb;
                    vec3 rgbSE = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, uTexelSize.y)).rgb;
                    vec3 rgbM = color;
                    
                    // Calculate luminance for each sample
                    float lumaNW = getLuma(rgbNW);
                    float lumaNE = getLuma(rgbNE);
                    float lumaSW = getLuma(rgbSW);
                    float lumaSE = getLuma(rgbSE);
                    float lumaM = getLuma(rgbM);
                    
                    // Calculate edge direction
                    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
                    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
                    
                    // Edge detection
                    vec2 dir;
                    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
                    dir.y = ((lumaNW + lumaSW) - (lumaNE + lumaSE));
                    
                    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * 0.25, 0.0078125);
                    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
                    
                    dir = min(vec2(4.0, 4.0), max(vec2(-4.0, -4.0), dir * rcpDirMin)) * uTexelSize;
                    
                    // Sample along edge
                    vec3 rgbA = 0.5 * (
                        texture2D(uTexture, vTexCoord + dir * (1.0/3.0 - 0.5)).rgb +
                        texture2D(uTexture, vTexCoord + dir * (2.0/3.0 - 0.5)).rgb
                    );
                    vec3 rgbB = rgbA * 0.5 + 0.25 * (
                        texture2D(uTexture, vTexCoord + dir * -0.5).rgb +
                        texture2D(uTexture, vTexCoord + dir * 0.5).rgb
                    );
                    
                    float lumaB = getLuma(rgbB);
                    
                    if (lumaB < lumaMin || lumaB > lumaMax) {
                        color = rgbA;
                    } else {
                        color = rgbB;
                    }
                }
                
                // ============================================
                // Denoise (Gaussian blur for noise reduction)
                // ============================================
                if (uEnableDenoise == 1) {
                    vec3 sum = vec3(0.0);
                    float radius = 1.0 + uDenoiseStrength * 1.5;  // 1.0 to 2.5 pixel radius
                    float count = 0.0;
                    
                    // 3x3 to 5x5 adaptive kernel based on strength
                    for (float x = -2.0; x <= 2.0; x += 1.0) {
                        for (float y = -2.0; y <= 2.0; y += 1.0) {
                            float dist = length(vec2(x, y));
                            if (dist <= radius) {
                                // Gaussian weight
                                float weight = exp(-(dist * dist) / (2.0 * radius * radius));
                                vec2 offset = vec2(x, y) * uTexelSize;
                                sum += texture2D(uTexture, vTexCoord + offset).rgb * weight;
                                count += weight;
                            }
                        }
                    }
                    
                    vec3 blurred = sum / count;
                    
                    // Edge-aware blending: preserve edges while smoothing flat areas
                    float edgeStrength = abs(getLuma(color) - getLuma(blurred));
                    float blendFactor = uDenoiseStrength * (1.0 - smoothstep(0.0, 0.1, edgeStrength));
                    color = mix(color, blurred, blendFactor);
                }
                
                // ============================================
                // Frame Blending (soap opera effect)
                // ============================================
                if (uEnableBlend == 1 && uHasPrevFrame == 1) {
                    vec3 prevColor = texture2D(uPrevTexture, vTexCoord).rgb;
                    float blendFactor = uBlendStrength * 0.5;
                    color = mix(color, prevColor, blendFactor);
                }
                
                // ============================================
                // Sharpening (unsharp mask)
                // ============================================
                if (uEnableSharpen == 1) {
                    vec3 n = texture2D(uTexture, vTexCoord + vec2(0.0, -uTexelSize.y)).rgb;
                    vec3 s = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y)).rgb;
                    vec3 e = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb;
                    vec3 w = texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x, 0.0)).rgb;
                    
                    vec3 edge = -4.0 * color + n + s + e + w;
                    color = color - edge * uSharpenStrength * 0.3;
                }
                
                // ============================================
                // Deband (removes color banding with dithering)
                // ============================================
                if (uEnableDeband == 1) {
                    // Add subtle noise to break up banding
                    float noise = (rand(vTexCoord) - 0.5) * uDebandStrength * 0.02;
                    color += vec3(noise);
                    
                    // Sample neighbors and smooth out banding
                    vec3 n1 = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x * 2.0, 0.0)).rgb;
                    vec3 n2 = texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x * 2.0, 0.0)).rgb;
                    vec3 n3 = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y * 2.0)).rgb;
                    vec3 n4 = texture2D(uTexture, vTexCoord + vec2(0.0, -uTexelSize.y * 2.0)).rgb;
                    
                    vec3 avg = (n1 + n2 + n3 + n4) * 0.25;
                    
                    // Only smooth when colors are very similar (banding detection)
                    float diff = length(color - avg);
                    if (diff < 0.03 * uDebandStrength) {
                        color = mix(color, avg, uDebandStrength * 0.5);
                    }
                }
                
                // ============================================
                // Fake HDR (SDR to fake HDR simulation)
                // ============================================
                if (uEnableHDR == 1) {
                    color = pow(color, vec3(0.9));
                    color *= uHDRStrength;
                    color = color / (1.0 + color);
                    
                    float luma = getLuma(color);
                    color = mix(vec3(luma), color, 1.15);
                }
                
                // ============================================
                // Color Grading (brightness, contrast, saturation, temperature)
                // ============================================
                // Brightness
                color += vec3(uBrightness);
                
                // Contrast (around 0.5 midpoint)
                color = (color - 0.5) * uContrast + 0.5;
                
                // Saturation
                float luma = getLuma(color);
                color = mix(vec3(luma), color, uSaturation);
                
                // Color Temperature (warm/cool shift)
                if (uColorTemperature != 0.0) {
                    // Warm = add red/yellow, reduce blue
                    // Cool = add blue, reduce red/yellow
                    float temp = uColorTemperature * 0.1;
                    color.r += temp;
                    color.g += temp * 0.5;
                    color.b -= temp;
                }
                
                // Final clamp
                color = clamp(color, 0.0, 1.0);
                
                gl_FragColor = vec4(color, 1.0);
            }
        """
        
        // Simple copy shader for copying OES texture to FBO
        private const val COPY_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
        
        // Shader for copying regular 2D texture to FBO (for frame history)
        private const val TEXTURE_COPY_FRAGMENT_SHADER = """
            precision mediump float;
            
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}

