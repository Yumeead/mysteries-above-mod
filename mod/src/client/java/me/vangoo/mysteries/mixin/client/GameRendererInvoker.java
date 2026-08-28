package me.vangoo.mysteries.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Другий міксин мода: відкриває приватний {@code GameRenderer.setPostEffect}.
 *
 * <p>Гасити ланцюг уміє публічний {@code clearPostEffect()}, ставити — ні: ванілька
 * ставить його лише сама, коли гравець дивиться очима крипера чи павука. Готового
 * гачка в Fabric API немає — ні в {@code fabric-rendering-v1}, ні деінде, — тож це
 * рівно той випадок, коли порядок пошуку доходить до останнього кроку.
 *
 * <h2>Чому це найдешевший різновид міксину</h2>
 *
 * <p>{@code @Invoker} нічого не вставляє в тіло методу — він лише відкриває доступ до
 * вже наявного. Переписати тіло {@code setPostEffect} можна як завгодно: гачок не
 * помітить. Ламається він рівно тоді, коли метод перейменують або приберуть, і ламається
 * гучно, на завантаженні гри, а не тихо посеред гри.
 */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("setPostEffect")
    void mysteries_above$setPostEffect(Identifier id);
}
