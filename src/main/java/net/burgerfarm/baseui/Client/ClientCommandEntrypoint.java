package net.burgerfarm.baseui.Client;

import net.burgerfarm.baseui.BaseUIMod;
import net.burgerfarm.baseui.Client.Screens.BaseUIClientScreen;
import net.burgerfarm.baseui.Commands.BaseUICommands;
import net.burgerfarm.baseui.Core.BaseUIElement;
import net.minecraft.client.gui.GuiGraphics;
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
        BaseUICommands.registerClient(
            event.getDispatcher(),
            () -> BaseUIClientScreen.open(EmptyRootElement::new),
            ClientCommandEntrypoint::openFallbackOverlayTestScreen);
    }

    private static void openFallbackOverlayTestScreen() {
        BaseUIClientScreen.open(() -> {
            throw new RuntimeException("Synthetic fallback test command failure");
        });
    }

    private static final class EmptyRootElement extends BaseUIElement<EmptyRootElement> {
        @Override
        protected void drawSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, float finalAlpha) {
        }
    }
}
