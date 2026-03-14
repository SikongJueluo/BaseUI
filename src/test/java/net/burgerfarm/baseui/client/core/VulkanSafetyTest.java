package net.burgerfarm.baseui.client.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class VulkanSafetyTest {
    @Test
    public void testNoGL11DirectCalls() throws Exception {
        Path sourceDir = Paths.get("src/main/java/net/burgerfarm/baseui");
        if (!Files.exists(sourceDir)) {
            // Might be running from root
            sourceDir = Paths.get("src/main/java/net/burgerfarm/baseui");
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> {
                     try {
                         String content = Files.readString(p);
                         if (content.contains("import org.lwjgl.opengl.GL11;") || content.contains("GL11.")) {
                             fail("Vulkan safety violation: Direct GL11 call found in " + p.toString() + ". Use GuiGraphics or RenderSystem instead.");
                         }
                     } catch (Exception e) {
                         fail("Failed to read " + p);
                     }
                 });
        }
    }
}
