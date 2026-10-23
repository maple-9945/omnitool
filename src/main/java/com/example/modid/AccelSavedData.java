package com.example.modid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * 把「正在加速的方块」写进世界存档，退出/重进世界后加速状态不会丢。
 * 数据在 TimeAccelHandler 里维护，这里只负责存取。
 */
public class AccelSavedData extends WorldSavedData {

    public static final String DATA_NAME = "omnitool_time_accel";

    private static AccelSavedData cached;

    public AccelSavedData() {
        super(DATA_NAME);
    }

    public AccelSavedData(String name) {
        super(name);
    }

    /** 服务端调用：取得（或创建）主世界的加速数据 */
    public static AccelSavedData get(World world) {
        if (world == null || world.isRemote) return null;
        MapStorage storage = world.getMapStorage();
        WorldSavedData data = storage.getOrLoadData(AccelSavedData.class, DATA_NAME);
        if (data == null) {
            data = new AccelSavedData();
            storage.setData(DATA_NAME, data);
        }
        cached = (AccelSavedData) data;
        return cached;
    }

    /** 加速状态变化时调用；WorldSavedData 在下一次世界保存时写盘 */
    public static void markDirtyIfPresent() {
        if (cached != null) cached.markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        TimeAccelHandler.loadState(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        TimeAccelHandler.saveState(nbt);
        return nbt;
    }
}
