package com.example.modid;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 配置文件：config/omnitool.cfg
 *   [timeAccel] timeAccelMultiplier = 1024   (1~1024)
 * 修改后重启 / 重载配置即可生效（服务端与客户端读同一份默认值）。
 */
public final class OmniConfig {

    public static final int DEFAULT_MULTIPLIER = 1024;
    public static int timeAccelMultiplier = DEFAULT_MULTIPLIER;
    public static boolean debug = false;

    private OmniConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        try {
            config.load();
            timeAccelMultiplier = config.getInt(
                    "timeAccelMultiplier", "timeAccel", DEFAULT_MULTIPLIER, 1, TimeAccelHandler.MAX_MULTIPLIER,
                    "默认时间加速倍率（1-1024），GUI 里设置的玩家值会覆盖它");
            debug = config.getBoolean("debug", "general", false,
                    "输出时间加速调试日志（每 20 ticks 打印一次机器进度变化）");
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[Omni Tool] 读取配置失败，使用默认倍率 {}", DEFAULT_MULTIPLIER, e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
        TimeAccelHandler.setGlobalMultiplier(timeAccelMultiplier);
    }
}
