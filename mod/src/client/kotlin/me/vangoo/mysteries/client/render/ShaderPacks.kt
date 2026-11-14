package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/**
 * Iris, when it is installed — reached by reflection, so the mod neither depends on it nor needs it.
 *
 * Under a shader pack the world is drawn by the pack's programs, not by each pipeline's own shader,
 * and a pipeline the pack has no program for never reaches the screen: Iris logs it as missing from
 * its override list, and the light it carried is simply gone. A pipeline that only changes a vanilla
 * one's blend can be handed to the pack's program for its kind ([assign]); one whose look *is* its
 * shader cannot, and its owner draws something plainer while a pack is in use ([inUse]).
 */
object ShaderPacks {

    private const val API = "net.irisshaders.iris.api.v0.IrisApi"
    private const val PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram"

    private val log = LoggerFactory.getLogger("mysteries_above/shader-packs")

    private val api: Any? = if (!FabricLoader.getInstance().isModLoaded("iris")) null else runCatching {
        Class.forName(API).getMethod("getInstance").invoke(null)
    }.onFailure { log.warn("Iris is loaded, but its API is not where it used to be", it) }.getOrNull()

    private val inUseCheck = api?.let { runCatching { Class.forName(API).getMethod("isShaderPackInUse") }.getOrNull() }

    /** Whether a shader pack draws the world right now. */
    val inUse: Boolean get() = inUseCheck?.invoke(api) as? Boolean ?: false

    /**
     * Has a pack draw [pipeline] with its program for [program] — the name of an `IrisProgram`, such as
     * `PARTICLES_TRANSLUCENT`. Without Iris it does nothing. Returns the pipeline, to be used in place.
     */
    fun assign(pipeline: RenderPipeline, program: String): RenderPipeline {
        api ?: return pipeline
        runCatching {
            val programs = Class.forName(PROGRAM)
            val value = programs.enumConstants.first { (it as Enum<*>).name == program }
            Class.forName(API).getMethod("assignPipeline", RenderPipeline::class.java, programs).invoke(api, pipeline, value)
        }.onFailure { log.warn("Could not hand {} to the shader pack's {}", pipeline.location, program, it) }
        return pipeline
    }
}
