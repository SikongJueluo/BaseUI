package net.burgerfarm.baseui.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

public class BaseUICommands {
    public static final String EXAMPLE_COMMAND_NAME = "baseui_example";
    public static final String FALLBACK_TEST_COMMAND_NAME = "baseui_fallback_test";

    private BaseUICommands() {
    }

    public static void registerClient(
        CommandDispatcher<CommandSourceStack> dispatcher,
        Runnable openExampleScreen,
        Runnable triggerFallbackOverlayTest
    ) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal(EXAMPLE_COMMAND_NAME)
                .requires(source -> source.getEntity() instanceof Player)
                .executes(context -> {
                    openExampleScreen.run();
                    return Command.SINGLE_SUCCESS;
                }));

        dispatcher.register(
            net.minecraft.commands.Commands.literal(FALLBACK_TEST_COMMAND_NAME)
                .requires(source -> source.getEntity() instanceof Player)
                .executes(context -> {
                    triggerFallbackOverlayTest.run();
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
