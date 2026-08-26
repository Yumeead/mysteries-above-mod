package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Клієнт → сервер: «кастуй те, що я тримаю».
 *
 * Порожній навмисно. Ідентичність здібності клієнту **не довіряється**: він однаково не
 * знає ні списку здібностей гравця, ні того, чи вони ще в нього є, а пакет із чужим
 * ключем усередині довелося б перевіряти на сервері рівно тим самим пошуком, що й
 * порожній. Вибір живе на сервері (`AbilitySelection`), клавіша лише натискає спуск.
 *
 * Коли фаза 11 принесе колесо здібностей, вибір поїде тим самим шляхом — окремим
 * пакетом вибору, а не полем усередині цього.
 */
class CastRequestPayload private constructor() : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CastRequestPayload> = TYPE

    companion object {

        val INSTANCE: CastRequestPayload = CastRequestPayload()

        val TYPE: CustomPacketPayload.Type<CastRequestPayload> =
            CustomPacketPayload.Type(modId("cast_request"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CastRequestPayload> =
            StreamCodec.unit(INSTANCE)
    }
}
