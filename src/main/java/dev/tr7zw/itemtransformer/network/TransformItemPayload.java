package dev.tr7zw.itemtransformer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TransformItemPayload(Identifier targetItemId, int heldSlot, int sourceSlot) implements CustomPacketPayload {

    public static final Type<TransformItemPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("itemtransformer", "transform_item"));

    public static final StreamCodec<FriendlyByteBuf, TransformItemPayload> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, TransformItemPayload::targetItemId,
        ByteBufCodecs.VAR_INT, TransformItemPayload::heldSlot,
        ByteBufCodecs.VAR_INT, TransformItemPayload::sourceSlot,
        TransformItemPayload::new
    );

    /** @return true when this is a direct transform (source == held slot). */
    public boolean isDirectTransform() {
        return heldSlot == sourceSlot;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
