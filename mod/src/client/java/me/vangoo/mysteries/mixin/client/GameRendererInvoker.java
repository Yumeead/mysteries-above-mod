package me.vangoo.mysteries.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The mod's second mixin: it opens up the private {@code GameRenderer.setPostEffect}.
 *
 * <p>The public {@code clearPostEffect()} can take a chain down, but not put one up: vanilla
 * only sets it itself, when the player looks through a creeper's or a spider's eyes. There is
 * no ready hook in the Fabric API — neither in {@code fabric-rendering-v1} nor anywhere else —
 * so this is exactly the case where the search order reaches its last step.
 *
 * <h2>Why this is the cheapest kind of mixin</h2>
 *
 * <p>{@code @Invoker} inserts nothing into a method body — it only opens access to what is
 * already there. The body of {@code setPostEffect} may be rewritten however one likes: the hook
 * will not notice. It breaks exactly when the method is renamed or removed, and it breaks
 * loudly, while the game loads, rather than quietly in the middle of play.
 */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("setPostEffect")
    void mysteries_above$setPostEffect(Identifier id);
}
