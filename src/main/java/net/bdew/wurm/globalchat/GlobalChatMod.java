package net.bdew.wurm.globalchat;

import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Communicator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.ClassPool;
import javassist.CtClass;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.MessagePolicy;
import org.gotti.wurmunlimited.modloader.interfaces.PlayerMessageListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerPollListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

public class GlobalChatMod implements WurmServerMod, Configurable, PreInitable, Initable, PlayerMessageListener, ServerStartedListener, ServerPollListener {
    private static final Logger logger = Logger.getLogger("GlobalChatMod");

    static String botToken;

    static String serverName;

    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static void logWarning(String msg) {
        if (logger != null)
            logger.log(Level.WARNING, msg);
    }

    public static void logInfo(String msg) {
        if (logger != null)
            logger.log(Level.INFO, msg);
    }

    public void configure(Properties properties) {
        botToken = properties.getProperty("botToken");
        serverName = properties.getProperty("serverName");
        CustomChannel.GLOBAL.discordName = properties.getProperty("globalEnName");
        CustomChannel.RECRUITMENT.discordName = properties.getProperty("globalDeName");
        CustomChannel.HELP.discordName = properties.getProperty("helpName");
        CustomChannel.TICKETS.discordName = properties.getProperty("ticketName");
        CustomChannel.GLOBAL.msg=properties.getProperty("globalMsg");
        CustomChannel.INFO.msg=properties.getProperty("infoMsg");
        CustomChannel.RECRUITMENT.msg=properties.getProperty("recruitmentMsg");
        CustomChannel.HELP.msg=properties.getProperty("helpMsg");

    }

    public void preInit() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            CtClass ctPlayers = classPool.getCtClass("com.wurmonline.server.Players");
            ctPlayers.getMethod("sendGlobalKingdomMessage", "(Lcom/wurmonline/server/creatures/Creature;JLjava/lang/String;Ljava/lang/String;ZBIII)V")
                    .insertBefore(" if (kingdom < 0) {net.bdew.wurm.globalchat.ChatHandler.sendMessage($$); return;}");
            ctPlayers.getMethod("sendStartKingdomChat", "(Lcom/wurmonline/server/players/Player;)V").setBody("return;");
            ctPlayers.getMethod("sendStartGlobalKingdomChat", "(Lcom/wurmonline/server/players/Player;)V").setBody("return;");
            CtClass ctLoginHandler = classPool.getCtClass("com.wurmonline.server.LoginHandler");
            ctLoginHandler.getMethod("sendLoggedInPeople", "(Lcom/wurmonline/server/players/Player;)V")
                    .insertBefore("net.bdew.wurm.globalchat.ChatHandler.sendBanner(player);");
            CtClass ctPlayer = classPool.getCtClass("com.wurmonline.server.players.Player");
            ctPlayer.getMethod("isKingdomChat", "()Z").setBody("return false;");
            ctPlayer.getMethod("isGlobalChat", "()Z").setBody("return false;");
            ctPlayer.getMethod("seesPlayerAssistantWindow", "()Z").setBody("return false;");
            CtClass CtServer = classPool.getCtClass("com.wurmonline.server.Server");
            CtServer.getMethod("shutDown", "()V").insertBefore("net.bdew.wurm.globalchat.ChatHandler.serverStopped();");
            CtServer.getMethod("broadCastNormal", "(Ljava/lang/String;Z)V").insertBefore("net.bdew.wurm.globalchat.ChatHandler.handleBroadcast($1);");
            CtServer.getMethod("broadCastSafe", "(Ljava/lang/String;ZB)V").insertBefore("net.bdew.wurm.globalchat.ChatHandler.handleBroadcast($1);");
            CtServer.getMethod("broadCastAlert", "(Ljava/lang/String;ZB)V").insertBefore("net.bdew.wurm.globalchat.ChatHandler.handleBroadcast($1);");
            classPool.getCtClass("com.wurmonline.server.ServerEntry").getMethod("setAvailable", "(ZZIIII)V")
                    .insertBefore("if (this.isAvailable != $1) net.bdew.wurm.globalchat.ChatHandler.serverAvailable(this, $1);");
            classPool.getCtClass("com.wurmonline.server.support.Tickets").getMethod("addTicket", "(Lcom/wurmonline/server/support/Ticket;Z)Lcom/wurmonline/server/support/Ticket;")
                    .insertBefore("net.bdew.wurm.globalchat.TicketHandler.updateTicket($1);");
            classPool.getCtClass("com.wurmonline.server.support.Ticket").getMethod("addTicketAction", "(Lcom/wurmonline/server/support/TicketAction;)V")
                    .insertBefore("net.bdew.wurm.globalchat.TicketHandler.addTicketAction(this, $1);");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public boolean onPlayerMessage(Communicator communicator, String message) {
        return false;
    }

    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title) {
        if (communicator.player.getPower() >= 4 && message.startsWith("#discordreconnect")) {
            DiscordHandler.initJda();
            return MessagePolicy.DISCARD;
        }
        if (communicator.player.getPower() >= 1 && message.startsWith("#eventmsg")) {
            String msg = message.replace("#eventmsg", "").trim();
            ChatHandler.setUpcomingEvent(msg);
            if (msg.length() > 0) {
                communicator.sendNormalServerMessage("Set event line: " + msg);
            } else {
                communicator.sendNormalServerMessage("Cleared event line.");
            }
            return MessagePolicy.DISCARD;
        }
        if (!message.startsWith("#") && !message.startsWith("/")) {
            CustomChannel chan = CustomChannel.findByIngameName(title);
            if (chan != null) {
                if (chan.canPlayersSend)
                    ChatHandler.handleGlobalMessage(chan, communicator, message);
                return MessagePolicy.DISCARD;
            }
            return MessagePolicy.PASS;
        }
        return MessagePolicy.PASS;
    }

    public void init() {}

    public void onServerStarted() {
        ChatHandler.serverStarted();
    }

    public void onServerPoll() {
        if (Servers.localServer.LOGINSERVER)
            DiscordHandler.poll();
    }
}
