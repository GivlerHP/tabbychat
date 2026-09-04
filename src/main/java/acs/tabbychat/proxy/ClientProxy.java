package acs.tabbychat.proxy;

import acs.tabbychat.core.GuiNewChatTC;
import acs.tabbychat.core.TabbyChat;
import acs.tabbychat.network.TabbyChatNetwork;
import acs.tabbychat.util.TabbyChatUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import acs.tabbychat.core.GuiChatTC;

public class ClientProxy extends CommonProxy {
    @Override
    public void load(FMLInitializationEvent event) {
        TabbyChatUtils.startup();
        TabbyChatNetwork.setClientReceiver((input, suggestions, usage) -> Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof GuiChatTC chat)
                    chat.receiveEnhancedSuggestions(input, suggestions, usage);
            }));
        FMLCommonHandler.instance().bus().register(this);
        TabbyChat.modLoaded = true;
    }

    @SubscribeEvent
    public void postLoad(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        TabbyChatNetwork.resetServerCapability();
        //ensure the chat gets loaded quickly
        GuiNewChatTC.getInstance().initializeForCurrentServer();
    }

    @SubscribeEvent
    public void onTick(TickEvent.RenderTickEvent event) {
        if (!event.phase.equals(TickEvent.Phase.START) && Minecraft.getMinecraft().theWorld != null) {
            onTickInGui(Minecraft.getMinecraft());
        }
    }

    private boolean onTickInGui(Minecraft minecraft) {
        TabbyChatUtils.chatGuiTick(minecraft);
        return true;
    }
}
