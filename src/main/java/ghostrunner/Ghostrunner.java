package ghostrunner;

import ghostrunner.api.GhostrunnerCommand;
import ghostrunner.handler.GhostrunnerServerEvents;
import ghostrunner.item.GRItems;
import ghostrunner.network.GhostrunnerNetworking;
import ghostrunner.network.GhostrunnerServerHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ghostrunner implements ModInitializer {
    public static final String MOD_ID = "ghostrunner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 项目级 ID 工具。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Online.");

        // 网络与事件
        GhostrunnerNetworking.registerPayloads();
        GhostrunnerServerHandlers.register();
        GhostrunnerServerEvents.register();

        // 内容注册
        GRItems.register();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register((FabricCreativeModeTabOutput entries) ->
                        entries.accept(GRItems.GHOSTRUNNER_TAG));

        // 命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GhostrunnerCommand.register(dispatcher));
    }
}