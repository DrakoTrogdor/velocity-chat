package me.confor.velocity.chat.modules;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.confor.velocity.chat.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import org.slf4j.Logger;

import java.util.*;

public class GlobalChat {
    private final ProxyServer server;
    private final Logger logger;
    private final Config config;

    public GlobalChat(ProxyServer server, Logger logger, Config config) {
        this.server = server;
        this.logger = logger;
        this.config = config;

        logger.info("Enabled global chat module");
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPlayerChat(PlayerChatEvent event) {
        String player = event.getPlayer().getUsername();
        String message = event.getMessage();
        String input = config.getString("chat.msg_chat");

        Component msg = parseMessage(input, List.of(
                new ChatTemplate("player", player, false),
                new ChatTemplate("message", message, config.getBool("chat.parse_player_messages"))
        ));

        if (this.config.getBool("chat.log_to_console"))
            this.logger.info("GLOBAL: <{}> {}", player, message);

        sendMessage(msg);

        if (!this.config.getBool("chat.passthrough"))
            event.setResult(ChatResult.denied());
    }

    @Subscribe
    public void onConnect(LoginEvent event) {
        String input = config.getString("chat.msg_join");

        Component msg = parseMessage(input, List.of(
                new ChatTemplate("player", event.getPlayer().getUsername(), false)
        ));

        sendMessage(msg);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        String input = config.getString("chat.msg_quit");

        Component msg = parseMessage(input, List.of(
                new ChatTemplate("player", event.getPlayer().getUsername(), false)
        ));

        sendMessage(msg);
    }

    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event) {
        Optional<ServerConnection> server = event.getPlayer().getCurrentServer();
        RegisteredServer oldServer = event.getPreviousServer();

        // if there isn't a previous server, then its the first connection
        if (server.isEmpty() || oldServer == null) // why are some fields @Nullable and others Optional<T>?
            return;

        String input = config.getString("chat.msg_switch");

        Component msg = parseMessage(input, List.of(
                new ChatTemplate("player", event.getPlayer().getUsername(), false),
                new ChatTemplate("server", server.get().getServerInfo().getName(), false),
                new ChatTemplate("oldserver", oldServer.getServerInfo().getName(), false)
        ));

        sendMessage(msg);
    }

    private Component parseMessage(String input, List<ChatTemplate> templates) {
        List<Template> list = new ArrayList<>();

        for (ChatTemplate tmpl : templates) {
            if (tmpl.parse)
                list.add(Template.of(tmpl.name, tmpl.value));
            else
                list.add(Template.of(tmpl.name, Component.text(tmpl.value)));
        }

        return MiniMessage.get().parse(input, list);
    }

    private void sendMessage(Component msg) {
        for (RegisteredServer server : this.server.getAllServers())
            server.sendMessage(msg);
    }

    class ChatTemplate {
        final String name;
        final String value;
        final Boolean parse; // should we run through minimessage's parsing?

        public ChatTemplate(String name, String value, Boolean shouldParse) {
            this.name = name;
            this.value = value;
            this.parse = shouldParse;
        }

        // <zml#2468> you'd want to use Component templates, not String templates
        // > the template system, allows you to choose between string and full components as replacements.
        // > These are executed in the main parse loop, so the string replacements can not contain MiniMessage Tags!
    }
}
