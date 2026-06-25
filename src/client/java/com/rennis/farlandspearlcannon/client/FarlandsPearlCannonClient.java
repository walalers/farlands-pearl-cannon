package com.rennis.farlandspearlcannon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.network.OpenDialPayload;

public class FarlandsPearlCannonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlueprintGuideClient.initialize();
        HudElementRegistry.addLast(FarlandsPearlCannonMod.id("blueprint_checklist"), new BlueprintHudOverlay());

        ClientPlayNetworking.registerGlobalReceiver(OpenDialPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new DirectionDialScreen(payload.corePos(), payload.targetIndex()))));
    }
}
