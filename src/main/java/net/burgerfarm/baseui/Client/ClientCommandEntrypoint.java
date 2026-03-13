package net.burgerfarm.baseui.Client;

import net.burgerfarm.baseui.BaseUIMod;
import net.burgerfarm.baseui.Commands.BaseUICommands;
import net.burgerfarm.baseui.Client.Screens.ExamplePlaceholderScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseUIMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientCommandEntrypoint {
    private ClientCommandEntrypoint() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BaseUICommands.registerClient(event.getDispatcher(), ExamplePlaceholderScreen::openOnClientThread);
    }
}
