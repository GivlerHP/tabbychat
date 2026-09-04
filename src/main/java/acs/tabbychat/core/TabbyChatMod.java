package acs.tabbychat.core;

import acs.tabbychat.proxy.CommonProxy;
import acs.tabbychat.network.TabbyChatNetwork;
import acs.tabbychat.util.TabbyChatUtils;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(name = TabbyChatUtils.name, modid = TabbyChatUtils.modid, version = TabbyChatUtils.version,
     acceptableRemoteVersions = "*")
public class TabbyChatMod {

    @SidedProxy(serverSide = "acs.tabbychat.proxy.ServerProxy", clientSide = "acs.tabbychat.proxy.ClientProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void load(FMLInitializationEvent event) {
        TabbyChatNetwork.init();
        proxy.load(event);
    }
}
