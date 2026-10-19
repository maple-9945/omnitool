package com.example.modid;

import com.example.modid.network.OmniNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "omnitool", name = "Omni Tool", version = "1.0.0")
public class ExampleMod {

    public static final Logger LOGGER = LogManager.getLogger("Omni Tool");

    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", "Omni Tool");
        // 1.12.2 网络通道注册（SimpleNetworkWrapper）
        OmniNetwork.register();
    }

}
