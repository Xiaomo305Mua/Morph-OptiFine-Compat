package com.xiaomo305mua.morphoptifinecompat;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = MOFCMod.MODID,
        name = MOFCMod.NAME,
        version = MOFCMod.VERSION,
        dependencies = "@modDependencies@")
public class MOFCMod {

    public static final String MODID = "@modId@";
    public static final String NAME = "@modName@";
    public static final String VERSION = "@modVersion@";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }
}