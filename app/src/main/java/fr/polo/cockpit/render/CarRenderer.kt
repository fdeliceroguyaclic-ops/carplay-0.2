package fr.polo.cockpit.render

import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class CarView(val label: String) { FRONT("Avant"), REAR("Arrière"), SIDE("Côté"), THREE_QUARTER("3/4"), INTERIOR("Intérieur") }

/**
 * Rendu 3D du véhicule avec Filament (PBR, glTF 2.0 / GLB).
 *
 * Conventions de nommage attendues dans le modèle (voir docs/MODELE_3D.md) :
 *  - matériaux/nœuds de carrosserie : "paint", "body", "carrosserie"
 *  - phares : "headlight", "phare"        - clignotants : "blinker", "clignotant" (+ "_l"/"_r")
 *  - animations : "door_fl", "door_fr", "door_rl", "door_rr", "trunk" (ou "porte_*", "coffre")
 */
class CarRenderer(context: Context) {
    companion object {
        init { Filament.init(); Gltfio.init() }
        const val MODEL_FILE = "models/polo.glb"
        fun importedModel(ctx: Context) = File(ctx.filesDir, MODEL_FILE)
    }

    private val appContext = context.applicationContext
    private val engine: Engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val sunEntity = EntityManager.get().create()
    private var indirectLight: IndirectLight? = null
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)
    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private val choreographer = Choreographer.getInstance()

    // Caméra orbitale
    private var yaw = 0.75; private var pitch = 0.18; private var distance = 7.5
    private var tYaw = yaw; private var tPitch = pitch; private var tDistance = distance
    private var interior = false
    var autoRotate = false

    // État des animations : index -> progression actuelle / cible
    private val animProgress = mutableMapOf<Int, Float>()
    private val animTarget = mutableMapOf<Int, Float>()
    private var headlights = false
    private var blinkLeft = false
    private var blinkRight = false
    private var blinkPhase = false
    private var lastBlinkToggle = 0L
    private var bodyRgb = floatArrayOf(0.95f, 0.95f, 0.94f)
    private var intro = 0f   // apparition progressive
    private var dirty = true

    var onModelInfo: ((ModelInfo) -> Unit)? = null

    data class ModelInfo(val loaded: Boolean, val animations: List<String>, val message: String)

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.antiAliasing = View.AntiAliasing.FXAA
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        camera.setExposure(16f, 1f / 125f, 100f)

        LightManager.Builder(LightManager.Type.SUN)
            .color(1f, 0.97f, 0.92f)
            .intensity(95_000f)
            .direction(0.4f, -1f, -0.5f)
            .castShadows(false)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)

        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.75f, 0.8f, 0.9f))
            .intensity(32_000f)
            .build(engine)
        scene.indirectLight = indirectLight
    }

    // ---------------------------------------------------------------- surface
    @SuppressLint("ClickableViewAccessibility")
    fun attach(textureView: TextureView) {
        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                dirty = true
            }
            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }
            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(38.0, width.toDouble() / max(1, height), 0.05, 100.0, Camera.Fov.VERTICAL)
                dirty = true
            }
        }
        uiHelper.attachTo(textureView)

        val scaleDetector = ScaleGestureDetector(textureView.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                tDistance = (tDistance / d.scaleFactor).coerceIn(if (interior) 0.0 else 4.5, 14.0)
                return true
            }
        })
        var lastX = 0f; var lastY = 0f
        textureView.setOnTouchListener { _, e ->
            scaleDetector.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; autoRotate = false }
                MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && !scaleDetector.isInProgress) {
                    tYaw -= (e.x - lastX) * 0.008
                    tPitch = (tPitch + (e.y - lastY) * 0.005).coerceIn(if (interior) -0.6 else 0.02, 1.2)
                    lastX = e.x; lastY = e.y
                }
                MotionEvent.ACTION_POINTER_UP -> { lastX = e.x; lastY = e.y }
            }
            dirty = true
            true
        }
        choreographer.postFrameCallback(frameCallback)
    }

    // ---------------------------------------------------------------- modèle
    /** Charge le modèle importé (prioritaire) ou celui embarqué dans l'APK. Retourne false si aucun. */
    fun loadModel(): Boolean {
        val bytes: ByteArray = runCatching { importedModel(appContext).takeIf { it.exists() }?.readBytes() }.getOrNull()
            ?: runCatching { appContext.assets.open(MODEL_FILE).use { it.readBytes() } }.getOrNull()
            ?: run { onModelInfo?.invoke(ModelInfo(false, emptyList(), "Aucun modèle 3D importé")); return false }

        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.flip()
        val a = assetLoader.createAsset(buffer)
            ?: run { onModelInfo?.invoke(ModelInfo(false, emptyList(), "Fichier GLB invalide")); return false }
        resourceLoader.loadResources(a)
        a.releaseSourceData()
        normalize(a)
        scene.addEntities(a.entities)
        asset = a
        animator = a.instance.animator
        val names = animator?.let { an -> (0 until an.animationCount).map { an.getAnimationName(it) } } ?: emptyList()
        names.indices.forEach { animProgress[it] = 0f; animTarget[it] = 0f }
        applyBodyColor()
        applyLights()
        intro = 0f
        dirty = true
        onModelInfo?.invoke(ModelInfo(true, names, "Modèle chargé — ${a.entities.size} éléments, ${names.size} animation(s)"))
        return true
    }

    /** Centre le modèle, pose ses roues au sol et le met à l'échelle ~4 m (longueur d'une Polo 6R : 3,97 m). */
    private fun normalize(a: FilamentAsset) {
        val box = a.boundingBox
        val c = box.center; val h = box.halfExtent
        val s = (3.97f / (2f * maxOf(h[0], h[1], h[2]))).takeIf { it.isFinite() && it > 0f } ?: 1f
        val tm = engine.transformManager
        val m = floatArrayOf(
            s, 0f, 0f, 0f,
            0f, s, 0f, 0f,
            0f, 0f, s, 0f,
            -c[0] * s, (-c[1] + h[1]) * s, -c[2] * s, 1f,
        )
        tm.setTransform(tm.getInstance(a.root), m)
    }

    // ---------------------------------------------------------------- commandes
    fun setView(v: CarView) {
        interior = v == CarView.INTERIOR
        when (v) {
            CarView.FRONT -> { tYaw = 0.0; tPitch = 0.12; tDistance = 7.0 }
            CarView.REAR -> { tYaw = PI; tPitch = 0.15; tDistance = 7.0 }
            CarView.SIDE -> { tYaw = PI / 2; tPitch = 0.08; tDistance = 7.5 }   // côté conducteur (gauche)
            CarView.THREE_QUARTER -> { tYaw = 0.75; tPitch = 0.18; tDistance = 7.5 }
            CarView.INTERIOR -> { tYaw = 0.0; tPitch = 0.0; tDistance = 0.0 }
        }
        dirty = true
    }

    fun setBodyColor(argb: Long) {
        bodyRgb = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)
        applyBodyColor(); dirty = true
    }

    fun setHeadlights(on: Boolean) { headlights = on; applyLights(); dirty = true }
    fun setBlinkers(left: Boolean, right: Boolean) { blinkLeft = left; blinkRight = right; applyLights(); dirty = true }

    /** Ouvre/ferme toutes les animations dont le nom contient l'un des mots-clés. Retourne false si aucune. */
    fun toggleAnimations(keywords: List<String>, open: Boolean): Boolean {
        val an = animator ?: return false
        var found = false
        for (i in 0 until an.animationCount) {
            val n = an.getAnimationName(i).lowercase()
            if (keywords.any { n.contains(it) }) { animTarget[i] = if (open) 1f else 0f; found = true }
        }
        dirty = true
        return found
    }

    fun toggleAnimation(index: Int, open: Boolean) { animTarget[index] = if (open) 1f else 0f; dirty = true }

    private fun forEachMaterial(block: (name: String, MaterialInstance) -> Unit) {
        val a = asset ?: return
        val rm = engine.renderableManager
        for (e in a.entities) {
            if (!rm.hasComponent(e)) continue
            val nodeName = (a.getName(e) ?: "").lowercase()
            val ri = rm.getInstance(e)
            for (p in 0 until rm.getPrimitiveCount(ri)) {
                val mi = rm.getMaterialInstanceAt(ri, p)
                // getName() selon la version de Filament : appel par réflexion pour rester compatible
                val matName = (runCatching { mi.javaClass.getMethod("getName").invoke(mi) as? String }.getOrNull() ?: "").lowercase()
                block("$nodeName|$matName", mi)
            }
        }
    }

    private fun applyBodyColor() = forEachMaterial { name, mi ->
        if (listOf("paint", "body", "carrosserie", "carpaint").any { name.contains(it) } &&
            mi.material.hasParameter("baseColorFactor")) {
            mi.setParameter("baseColorFactor", Colors.RgbaType.SRGB, bodyRgb[0], bodyRgb[1], bodyRgb[2], 1f)
        }
    }

    private fun applyLights() = forEachMaterial { name, mi ->
        if (!mi.material.hasParameter("emissiveFactor")) return@forEachMaterial
        val isHead = listOf("headlight", "phare", "head_light").any { name.contains(it) }
        val isBlink = listOf("blinker", "clignot", "indicator", "turn_signal").any { name.contains(it) }
        val on: Boolean
        val rgb: FloatArray
        when {
            isBlink -> {
                val left = listOf("_l", "left", "gauche", "_g").any { name.contains(it) }
                on = blinkPhase && (if (left) blinkLeft else blinkRight)
                rgb = floatArrayOf(1f, 0.55f, 0.05f)
            }
            isHead -> { on = headlights; rgb = floatArrayOf(1f, 0.97f, 0.9f) }
            else -> return@forEachMaterial
        }
        val k = if (on) 60f else 0f
        mi.setParameter("emissiveFactor", Colors.RgbType.LINEAR, rgb[0] * k, rgb[1] * k, rgb[2] * k)
    }

    // ---------------------------------------------------------------- boucle
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            update()
            if (!dirty) return  // économie CPU/GPU : pas de rendu si rien ne bouge
            dirty = false
            val sc = swapChain ?: return
            if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    private fun update() {
        if (autoRotate) { tYaw += 0.004; dirty = true }
        // lissage caméra
        fun lerp(a: Double, b: Double) = a + (b - a) * 0.15
        if (abs(yaw - tYaw) + abs(pitch - tPitch) + abs(distance - tDistance) > 1e-4) {
            yaw = lerp(yaw, tYaw); pitch = lerp(pitch, tPitch); distance = lerp(distance, tDistance); dirty = true
        }
        if (intro < 1f && asset != null) { intro = (intro + 0.02f).coerceAtMost(1f); dirty = true }

        if (interior) {
            // place conducteur (conduite à gauche) — à ajuster selon le modèle
            val ex = 0.36; val ey = 1.12; val ez = -0.25 + distance * 0.2
            camera.lookAt(ex, ey, ez, ex + sin(yaw) * 2, ey + sin(pitch), ez + cos(yaw) * 2, 0.0, 1.0, 0.0)
        } else {
            val d = distance + (1f - intro) * 6f
            val ty = 0.65
            camera.lookAt(
                d * cos(pitch) * sin(yaw), ty + d * sin(pitch), d * cos(pitch) * cos(yaw),
                0.0, ty, 0.0, 0.0, 1.0, 0.0,
            )
        }

        // clignotants ~1,5 Hz
        val now = System.currentTimeMillis()
        if ((blinkLeft || blinkRight) && now - lastBlinkToggle > 340) {
            blinkPhase = !blinkPhase; lastBlinkToggle = now; applyLights(); dirty = true
        }

        // animations portes/coffre
        val an = animator ?: return
        var moving = false
        for ((i, target) in animTarget) {
            val p = animProgress[i] ?: 0f
            if (abs(p - target) > 0.001f) {
                animProgress[i] = p + (target - p).coerceIn(-0.035f, 0.035f)
                moving = true
            }
        }
        if (moving || dirty) {
            for ((i, p) in animProgress) an.applyAnimation(i, p * an.getAnimationDuration(i))
            an.updateBoneMatrices()
            dirty = true
        }
    }

    fun destroy() {
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        asset?.let { scene.removeEntities(it.entities); assetLoader.destroyAsset(it) }
        asset = null; animator = null
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        engine.destroyEntity(sunEntity)
        EntityManager.get().destroy(sunEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}
