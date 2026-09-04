package acs.tabbychat.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TabbyChatNetwork {
    private static final int MAX_INPUT_LENGTH = 2048;
    private static final int MAX_USAGE_LENGTH = 1024;
    private static final int MAX_SUGGESTIONS = 256;
    private static final int MAX_SUGGESTION_LENGTH = 256;
    private static final int MAX_SUGGESTION_CHARS = 24000;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tabbychat");
    private static boolean initialized;
    private static volatile boolean serverAvailable;
    private static ClientSuggestionReceiver clientReceiver;

    private TabbyChatNetwork() {}

    public static void init() {
        if (initialized)
            return;
        initialized = true;
        CHANNEL.registerMessage(SuggestionRequestHandler.class, SuggestionRequest.class, 0, Side.SERVER);
        CHANNEL.registerMessage(SuggestionResponseHandler.class, SuggestionResponse.class, 1, Side.CLIENT);
    }

    public static void resetServerCapability() {
        serverAvailable = false;
    }

    public static boolean isServerAvailable() {
        return serverAvailable;
    }

    public static void setClientReceiver(ClientSuggestionReceiver receiver) {
        clientReceiver = receiver;
    }

    /** The first request also acts as a capability probe; an unmodded server simply ignores it. */
    public static void requestSuggestions(String input) {
        CHANNEL.sendToServer(new SuggestionRequest(input));
    }

    public static class SuggestionRequest implements IMessage {
        private String input = "";

        public SuggestionRequest() {}

        SuggestionRequest(String input) {
            this.input = safeString(input, MAX_INPUT_LENGTH);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.input = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, safeString(this.input, MAX_INPUT_LENGTH));
        }
    }

    public static class SuggestionResponse implements IMessage {
        private String input = "";
        private String usage = "";
        private List<String> suggestions = Collections.emptyList();

        public SuggestionResponse() {}

        SuggestionResponse(String input, String usage, List<String> suggestions) {
            this.input = safeString(input, MAX_INPUT_LENGTH);
            this.usage = safeString(usage, MAX_USAGE_LENGTH);
            this.suggestions = sanitizeSuggestions(suggestions);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.input = ByteBufUtils.readUTF8String(buf);
            this.usage = ByteBufUtils.readUTF8String(buf);
            int count = Math.min(buf.readUnsignedShort(), 512);
            this.suggestions = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                this.suggestions.add(ByteBufUtils.readUTF8String(buf));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, safeString(this.input, MAX_INPUT_LENGTH));
            ByteBufUtils.writeUTF8String(buf, safeString(this.usage, MAX_USAGE_LENGTH));
            List<String> safeSuggestions = sanitizeSuggestions(this.suggestions);
            int count = safeSuggestions.size();
            buf.writeShort(count);
            for (int i = 0; i < count; i++)
                ByteBufUtils.writeUTF8String(buf, safeSuggestions.get(i));
        }
    }

    public static class SuggestionRequestHandler
        implements IMessageHandler<SuggestionRequest, SuggestionResponse> {
        @Override
        public SuggestionResponse onMessage(SuggestionRequest message, MessageContext ctx) {
            String input = safeString(message.input, MAX_INPUT_LENGTH);
            if (message.input == null || message.input.length() > MAX_INPUT_LENGTH)
                return new SuggestionResponse("", "", Collections.emptyList());
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ICommandManager manager = MinecraftServer.getServer().getCommandManager();
            List<String> suggestions = Collections.emptyList();
            try {
                suggestions = manager.getPossibleCommands(player, input);
            }
            catch (Throwable ignored) {
                // A broken third-party command must not disconnect the player.
            }
            String commandName = firstCommand(input);
            String usage = "";
            try {
                Map<String, ICommand> commands = manager.getCommands();
                ICommand command = commands == null ? null : commands.get(commandName);
                if (command != null && command.canCommandSenderUseCommand(player))
                    usage = command.getCommandUsage(player);
            }
            catch (Throwable ignored) {
                // A broken third-party command must not break completion for the player.
            }
            return new SuggestionResponse(input, usage, suggestions);
        }

        private static String firstCommand(String input) {
            String command = input.startsWith("/") ? input.substring(1) : input;
            int space = command.indexOf(' ');
            return space < 0 ? command : command.substring(0, space);
        }
    }

    public static class SuggestionResponseHandler
        implements IMessageHandler<SuggestionResponse, IMessage> {
        @Override
        public IMessage onMessage(final SuggestionResponse message, MessageContext ctx) {
            serverAvailable = true;
            ClientSuggestionReceiver receiver = clientReceiver;
            if (receiver != null)
                receiver.receive(message.input, message.suggestions, message.usage);
            return null;
        }
    }

    public interface ClientSuggestionReceiver {
        void receive(String input, List<String> suggestions, String usage);
    }

    private static String safeString(String value, int maxLength) {
        if (value == null)
            return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static List<String> sanitizeSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty())
            return Collections.emptyList();
        List<String> result = new ArrayList<>(Math.min(suggestions.size(), MAX_SUGGESTIONS));
        int chars = 0;
        for (String suggestion : suggestions) {
            if (suggestion == null)
                continue;
            String safeSuggestion = safeString(suggestion, MAX_SUGGESTION_LENGTH);
            if (chars + safeSuggestion.length() > MAX_SUGGESTION_CHARS)
                break;
            result.add(safeSuggestion);
            chars += safeSuggestion.length();
            if (result.size() >= MAX_SUGGESTIONS)
                break;
        }
        return result;
    }
}
