package dev.tr7zw.itemtransformer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server→client response confirming ItemTransformer is installed. */
public record ServerProbeResponsePayload() implements CustomPacketPayload {

    public static final Type<ServerProbeResponsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("itemtransformer", "probe_response"));

    public static final StreamCodec<FriendlyByteBuf, ServerProbeResponsePayload> STREAM_CODEC =
        StreamCodec.unit(new ServerProbeResponsePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}