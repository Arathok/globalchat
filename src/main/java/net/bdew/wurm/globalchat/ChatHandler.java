package net.bdew.wurm.globalchat;

import com.wurmonline.server.*;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.webinterface.WcKingdomChat;
import net.bdew.wurm.tools.server.ServerThreadExecutor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatHandler {

    static Logger chatlogger = Logger.getLogger("Chat");
    static String eventsMsg = "";


    public static void handleGlobalMessage(CustomChannel channel, Communicator communicator, String message) {
        if (communicator.isInvulnerable()) {
            communicator.sendAlertServerMessage("You may not use global chat until you have moved and lost invulnerability.");
            return;
        }
        if (communicator.player.isMute()) {
            communicator.sendNormalServerMessage("You are muted.");
            return;
        }
        chatlogger.log(Level.INFO, channel.ingameName + "-" + "<" + communicator.player.getName() + "> " + message);

        sendToPlayers(channel, communicator.player.getName(), message, communicator.player.getWurmId(),
                communicator.player.hasColoredChat() ? communicator.player.getCustomRedChat() : -1,
                communicator.player.hasColoredChat() ? communicator.player.getCustomGreenChat() : -1,
                communicator.player.hasColoredChat() ? communicator.player.getCustomBlueChat() : -1
        );
        sendToServers(channel,
                communicator.player.getName(), message, communicator.player.getWurmId(),
                communicator.player.hasColoredChat() ? communicator.player.getCustomRedChat() : -1,
                communicator.player.hasColoredChat() ? communicator.player.getCustomGreenChat() : -1,
                communicator.player.hasColoredChat() ? communicator.player.getCustomBlueChat() : -1
        );

        if (Servers.localServer.LOGINSERVER)
            DiscordHandler.sendToDiscord(channel, "<" + communicator.player.getName() + "> " + message);

        communicator.player.chatted();
    }

    public static void sendMessage(final Creature sender, final long senderId, final String playerName, final String message, final boolean emote, final byte kingdom, final int r, final int g, final int b) {
        CustomChannel chan = CustomChannel.findByKingdom(kingdom);
        if (chan != null && !chan.discordOnly) {
            if (chan == CustomChannel.INFO) {
                eventsMsg = message.trim();
                if (eventsMsg.length() > 0)
                    sendToPlayers(CustomChannel.INFO, "", eventsMsg, -10L, 255, 140, 0);
            } else {
                if (Servers.localServer.LOGINSERVER)
                    DiscordHandler.sendToDiscord(chan, "<" + playerName + "> " + message);
                chatlogger.log(Level.INFO, chan.ingameName + "-" + "<" + playerName + "> " + message);
                sendToPlayers(chan, playerName, message, senderId, r, g, b);
            }
        }
    }

    public static void setUpcomingEvent(String msg) {
        eventsMsg = msg.trim();
        if (eventsMsg.length() > 0)
            sendToPlayers(CustomChannel.INFO, "", eventsMsg, -10L, 255, 140, 0);
        sendToServers(CustomChannel.INFO, "", eventsMsg, -10L, 0, 0, 0);
    }

    public static void systemMessage(Player player, CustomChannel channel, String msg) {
        systemMessage(player, channel, msg, 250, 150, 250);
    }

    public static void systemMessage(Player player, CustomChannel channel, String msg, int r, int g, int b) {
        Message mess = new Message(player, (byte) 16, channel.ingameName, msg, r, g, b);
        player.getCommunicator().sendMessage(mess);
    }

    public static void sendBanner(final Player player) {
        systemMessage(player, CustomChannel.GLOBAL, CustomChannel.GLOBAL.msg);
        systemMessage(player, CustomChannel.HELP, CustomChannel.HELP.msg);
        systemMessage(player, CustomChannel.INFO, CustomChannel.INFO.msg);
        systemMessage(player, CustomChannel.RECRUITMENT, CustomChannel.RECRUITMENT.msg);


        if (eventsMsg.length() > 0)
            systemMessage(player, CustomChannel.INFO, eventsMsg, 255, 140, 0);
    }

    static void sendToPlayersAndServers(CustomChannel channel, String author, String msg, long wurmId, int r, int g, int b) {
        sendToPlayers(channel, author, msg, wurmId, r, g, b);
        sendToServers(channel, author, msg, wurmId, r, g, b);
    }

    static void sendToPlayers(CustomChannel channel, String author, String msg, long wurmId, int r, int g, int b) {
        ServerThreadExecutor.INSTANCE.execute(() -> {
            Message mess = new Message(null, (byte) 16, channel.ingameName, (author.length() == 0 ? "" : "<" + author + "> ") + msg);
            mess.setColorR(r);
            mess.setColorG(g);
            mess.setColorB(b);
            final Player[] playarr = Players.getInstance().getPlayers();
            for (Player player : playarr) {
                if (!player.isIgnored(wurmId)) {
                    player.getCommunicator().sendMessage(mess);
                }
            }
        });
    }

    static void sendToServers(CustomChannel channel, String author, String msg, long wurmId, int r, int g, int b) {
        final WcKingdomChat wc = new WcKingdomChat(WurmId.getNextWCCommandId(), wurmId, author, msg, false, channel.kingdom, r, g, b);
        if (!Servers.isThisLoginServer()) {
            wc.sendToLoginServer();
        } else {
            wc.sendFromLoginServer();
        }
    }

    public static void serverStarted() {
        if (Servers.localServer.LOGINSERVER) {
            GlobalChatMod.logInfo("Server started, connecting to discord");
            DiscordHandler.initJda();
            DiscordHandler.sendToDiscord(CustomChannel.GLOBAL, "**Servers are starting up...**");
        }
    }

    public static void serverStopped() {
        if (Servers.localServer.LOGINSERVER) {
            GlobalChatMod.logInfo("Sending shutdown notice");
            DiscordHandler.sendToDiscord(CustomChannel.GLOBAL, "**Servers are shutting down. Byeeee~**");
        }
    }

    public static void serverAvailable(ServerEntry ent, boolean available) {
        if (Servers.localServer.LOGINSERVER) {
            GlobalChatMod.logInfo(String.format("Notifying available change - %s %s", ent.getName(), available));
            DiscordHandler.sendToDiscord(CustomChannel.GLOBAL, String.format("**%s is %s**", ent.getName(), available ? "now online!" : "shutting down."));
        }
    }

    public static void handleBroadcast(String msg) {
        if (msg.startsWith("The settlement of") || msg.startsWith("Rumours of") || msg.endsWith("has been slain.")) {
            ChatHandler.sendToPlayersAndServers(CustomChannel.GLOBAL, "[" + Servers.getLocalServerName() + "]", msg, -10L, 255, 140, 0);
        }
    }
}
