package dev.tr7zw.itemtransformer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Transform request where the source item is inside a Shulker Box. */
public record TransformShulkerPayload(
    Identifier targetItemId,
    int heldSlot,
    int shulkerSlot,
    int shulkerSubSlot
) implements CustomPacketPayload {

    public static final Type<TransformShulkerPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("itemtransformer", "transform_shulker"));

    public static final StreamCodec<FriendlyByteBuf, TransformShulkerPayload> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC, TransformShulkerPayload::targetItemId,
            ByteBufCodecs.VAR_INT, TransformShulkerPayload::heldSlot,
            ByteBufCodecs.VAR_INT, TransformShulkerPayload::shulkerSlot,
            ByteBufCodecs.VAR_INT, TransformShulkerPayload::shulkerSubSlot,
            TransformShulkerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}