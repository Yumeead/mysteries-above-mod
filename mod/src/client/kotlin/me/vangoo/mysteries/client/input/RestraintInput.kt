package me.vangoo.mysteries.client.input

import me.vangoo.mysteries.ability.Restraints
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

/**
 * The client half of [Restraints]: while the local player is bound, the keys they press move nothing. Walking,
 * strafing, jumping and sprinting are refused; sneaking stays, since crouching where one stands moves no-one. Their own
 * way along the ground is held at zero too, so a blow does not knock them out of their bonds before the server says so.
 */
object RestraintInput {

    @JvmStatic
    fun apply(input: ClientInput) {
        val player = Minecraft.getInstance().player ?: return
        if (!Restraints.isRestrained(player)) return
        input.keyPresses = Input(false, false, false, false, false, input.keyPresses.shift(), false)
        input.moveVector = Vec2.ZERO
        player.isSprinting = false
        val motion = player.deltaMovement
        if (motion.x != 0.0 || motion.z != 0.0) player.setDeltaMovement(0.0, motion.y, 0.0)
    }
}
