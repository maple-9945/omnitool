package com.example.modid.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * FTB 团队适配：同一 FTB 团队共用一张无线电网与同一套加速渲染。
 *
 * 不直接编译依赖 FTBLib，全部用反射调用（FTBLib 5.x / 1.12.2）：
 *   FTBLibAPI.getTeamID(UUID) -> short
 * 团队 key 用「固定命名空间 + 团队 short id」合成一个 UUID，
 * 这样无需改动现有存档的 members 结构（members 里存的就是这种 key）。
 * 没有安装 FTBLib 或玩家没有团队时，回退到玩家自己的 UUID（等价于原来的单人行为）。
 */
public final class WirelessTeams {

    private static final UUID TEAM_NAMESPACE = new UUID(0x4654425445414D00L, 0L);

    private static Boolean available;
    private static Method getTeamIdMethod;

    private WirelessTeams() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = Boolean.FALSE;
            try {
                if (Loader.isModLoaded("ftblib")) {
                    Class<?> api = Class.forName("com.feed_the_beast.ftblib.lib.data.FTBLibAPI");
                    getTeamIdMethod = api.getMethod("getTeamID", UUID.class);
                    available = Boolean.TRUE;
                }
            } catch (Throwable ignored) {
                // 没有 FTBLib（或版本不匹配）：回退到玩家个人
            }
        }
        return available;
    }

    public static UUID keyOf(EntityPlayer player) {
        return player == null ? null : keyOf(player.getUniqueID());
    }

    public static UUID keyOf(UUID playerId) {
        if (playerId == null) return null;
        if (!isAvailable()) return playerId;
        try {
            Object result = getTeamIdMethod.invoke(null, playerId);
            if (!(result instanceof Short)) return playerId;
            short teamId = (Short) result;
            if (teamId <= 0) return playerId;
            return new UUID(TEAM_NAMESPACE.getMostSignificantBits(), teamId);
        } catch (Throwable t) {
            return playerId;
        }
    }
}
