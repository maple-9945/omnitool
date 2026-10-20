package com.example.modid;

import com.example.modid.network.OmniNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "omnitool", name = "Omni Tool", version = "1.0.0")
public class ExampleMod {

    public static final Logger LOGGER = LogManager.getLogger("Omni Tool");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", "Omni Tool");
        // 1.12.2 网络通道注册（SimpleNetworkWrapper）
        OmniNetwork.register();
        // 读取 config/omnitool.cfg（时间加速默认倍率）
        OmniConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandOmniTool());
    }
}
