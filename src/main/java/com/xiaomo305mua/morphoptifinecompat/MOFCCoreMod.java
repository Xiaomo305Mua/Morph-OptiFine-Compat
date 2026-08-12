package com.xiaomo305mua.morphoptifinecompat;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("@mcVersion@")
@IFMLLoadingPlugin.TransformerExclusions({ "com.xiaomo305mua.morphoptifinecompat." })
public class MOFCCoreMod implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                "com.xiaomo305mua.morphoptifinecompat.MOFCTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}