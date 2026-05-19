package com.yuchen.ailedger.ui.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Stage 1 OpenGL glass probe.
 *
 * This is intentionally independent from the production glass registry. It only proves that
 * a transparent OpenGL ES shader layer can be mixed into the Compose scene on API 31 devices.
 */
@Composable
fun OpenGLGlassProbeLayer(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return
    AndroidView(
        modifier = modifier,
        factory = { context -> OpenGLGlassProbeView(context) },
        update = { view -> view.requestRender() }
    )
}

private class OpenGLGlassProbeView(context: Context) : GLSurfaceView(context) {
    private val probeRenderer = OpenGLGlassProbeRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(probeRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

private class OpenGLGlassProbeRenderer : GLSurfaceView.Renderer {
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(FULLSCREEN_QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var timeHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var startTimeNanos = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        startTimeNanos = System.nanoTime()

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val seconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000f
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniform1f(timeHandle, seconds)

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(glProgram)
            GLES20.glDeleteProgram(glProgram)
            error("OpenGL glass probe program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return glProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL glass probe shader compile failed: $log")
        }
        return shader
    }

    private companion object {
        val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform float uTime;

            float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
                vec2 q = abs(p) - (halfSize - vec2(radius));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            vec3 syntheticBackdrop(vec2 uv) {
                float blueBand = smoothstep(0.18, 0.48, uv.x) * (1.0 - smoothstep(0.56, 0.92, uv.x));
                float redBand = smoothstep(0.42, 0.70, uv.x) * (1.0 - smoothstep(0.82, 1.05, uv.x));
                float wave = 0.5 + 0.5 * sin((uv.x * 7.0 + uv.y * 3.0 + uTime * 0.15) * 3.14159);
                vec3 sky = mix(vec3(0.22, 0.34, 0.72), vec3(0.88, 0.92, 1.0), uv.y);
                vec3 color = sky;
                color = mix(color, vec3(0.20, 0.46, 1.0), blueBand * 0.72);
                color = mix(color, vec3(1.0, 0.22, 0.14), redBand * 0.76);
                color += vec3(0.07, 0.05, 0.10) * wave;
                return clamp(color, 0.0, 1.0);
            }

            vec3 softSample(vec2 uv, vec2 n, vec2 t, float radiusPx) {
                vec2 r = vec2(radiusPx) / uResolution;
                vec3 c = syntheticBackdrop(uv) * 0.28;
                c += syntheticBackdrop(uv + n * r.x * 0.75) * 0.15;
                c += syntheticBackdrop(uv - n * r.x * 0.75) * 0.15;
                c += syntheticBackdrop(uv + t * r.x * 0.42) * 0.10;
                c += syntheticBackdrop(uv - t * r.x * 0.42) * 0.10;
                c += syntheticBackdrop(uv + (n + t * 0.45) * r.x) * 0.08;
                c += syntheticBackdrop(uv + (n - t * 0.45) * r.x) * 0.08;
                c += syntheticBackdrop(uv - (n + t * 0.45) * r.x) * 0.03;
                c += syntheticBackdrop(uv - (n - t * 0.45) * r.x) * 0.03;
                return c;
            }

            void main() {
                vec2 coord = gl_FragCoord.xy;
                vec2 center = vec2(uResolution.x * 0.50, uResolution.y * 0.42);
                vec2 rectSize = vec2(uResolution.x * 0.76, uResolution.y * 0.145);
                float radius = min(rectSize.y * 0.48, 76.0);
                vec2 p = coord - center;
                float sd = roundedBoxSdf(p, rectSize * 0.5, radius);
                float mask = 1.0 - smoothstep(0.0, 2.0, sd);
                if (mask <= 0.001) {
                    discard;
                }

                float inside = max(-sd, 0.0);
                vec2 dx = vec2(1.0, 0.0);
                vec2 dy = vec2(0.0, 1.0);
                float gx = roundedBoxSdf(p + dx, rectSize * 0.5, radius) - roundedBoxSdf(p - dx, rectSize * 0.5, radius);
                float gy = roundedBoxSdf(p + dy, rectSize * 0.5, radius) - roundedBoxSdf(p - dy, rectSize * 0.5, radius);
                vec2 normal = normalize(vec2(gx, gy) + vec2(0.0001));
                vec2 tangent = vec2(-normal.y, normal.x);
                float corner = clamp(abs(normal.x * normal.y) * 2.2, 0.0, 1.0);

                float edgeWidth = rectSize.y * 0.34;
                float surfaceGate = clamp(1.0 - inside / max(edgeWidth * 1.42, 1.0), 0.0, 1.0);
                float edgeCore = exp(-inside / max(edgeWidth * 0.12, 1.0));
                float edgeShoulder = exp(-inside / max(edgeWidth * 0.45, 1.0)) * 0.38;
                float outerHighlight = exp(-inside / max(edgeWidth * 0.075, 1.0));
                float compressionBand = exp(-pow((inside - edgeWidth * 0.22) / max(edgeWidth * 0.15, 1.0), 2.0));
                float innerShadow = exp(-pow((inside - edgeWidth * 0.58) / max(edgeWidth * 0.23, 1.0), 2.0));
                float caustic = corner * exp(-inside / max(edgeWidth * 0.28, 1.0));

                vec2 uv = coord / uResolution;
                float pull = (edgeCore * 28.0 + edgeShoulder * 44.0 + caustic * 18.0) * surfaceGate;
                float tangentBend = sin((p.x + p.y) * 0.018) * edgeShoulder * 16.0;
                vec2 refractUv = uv + (normal * pull + tangent * tangentBend) / uResolution;

                vec3 base = syntheticBackdrop(uv);
                vec3 refracted = softSample(refractUv, normal, tangent, mix(3.0, 9.0, corner));
                vec3 color = mix(base, refracted, surfaceGate * 0.78);
                vec3 compressed = clamp((color - 0.5) * 1.24 + 0.5, 0.0, 1.0);
                color = mix(color, compressed, compressionBand * 0.23);
                color += vec3(outerHighlight * 0.16);
                color -= vec3(innerShadow * 0.09);
                color += vec3(caustic * 0.16, caustic * 0.13, caustic * 0.08);
                color = mix(color, vec3(1.0), 0.12);
                color = clamp(color, 0.0, 1.0);

                float alpha = mask * (0.28 + edgeCore * 0.24 + outerHighlight * 0.16 + caustic * 0.10);
                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}
