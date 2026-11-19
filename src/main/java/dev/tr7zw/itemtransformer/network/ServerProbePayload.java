package dev.tr7zw.itemtransformer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client→server probe to detect if the server has ItemTransformer. */
public record ServerProbePayload() implements CustomPacketPayload {

    public static final Type<ServerProbePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("itemtransformer", "probe"));

    public static final StreamCodec<FriendlyByteBuf, ServerProbePayload> STREAM_CODEC =
        StreamCodec.unit(new ServerProbePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}