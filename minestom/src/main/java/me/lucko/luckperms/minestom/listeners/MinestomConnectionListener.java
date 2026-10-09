/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.minestom.listeners;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.plugin.util.AbstractConnectionListener;
import me.lucko.luckperms.minestom.LPMinestomPlugin;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.player.GameProfile;

public final class MinestomConnectionListener extends AbstractConnectionListener {

    private final LPMinestomPlugin plugin;

    public MinestomConnectionListener(LPMinestomPlugin plugin, EventNode<Event> eventNode) {
        super(plugin);
        this.plugin = plugin;

        eventNode.addListener(AsyncPlayerPreLoginEvent.class, this::onPlayerPreLogin);
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onPlayerLogin);
        // Configuration players are not in Minestom's online player collection yet.
        // Keep their user data protected from the housekeeper until they spawn.
        eventNode.addListener(PlayerSpawnEvent.class, event -> handleLoggedIn(event.getPlayer().getUuid()));
        eventNode.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    private void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            this.plugin.getBootstrap().getEnableLatch().await(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        GameProfile profile = event.getGameProfile();
        UUID uuid = profile.uuid();
        String username = profile.name();

        if (this.plugin.getConfiguration().get(ConfigKeys.DEBUG_LOGINS)) {
            this.plugin.getLogger().info("Processing pre-login for " + uuid + " - " + username);
        }

        if (!event.getConnection().isOnline()) {
            this.plugin.getLogger().info("Another plugin has cancelled the connection for " + uuid + " - " + username + ". No permissions data will be loaded.");
            return;
        }

        try {
            User user = loadUser(uuid, username);
            recordConnection(uuid);
            this.plugin.getEventDispatcher().dispatchPlayerLoginProcess(uuid, username, user);
        } catch (Exception ex) {
            this.plugin.getLogger().severe("Exception occurred whilst loading data for " + uuid + " - " + username, ex);

            Component reason = TranslationManager.render(Message.LOADING_DATABASE_ERROR.build());
            event.getConnection().kick(reason);
            this.plugin.getEventDispatcher().dispatchPlayerLoginProcess(uuid, username, null);
        }
    }

    private void onPlayerLogin(AsyncPlayerConfigurationEvent event) {
        final Player player = event.getPlayer();

        if (this.plugin.getConfiguration().get(ConfigKeys.DEBUG_LOGINS)) {
            this.plugin.getLogger().info("Processing login for " + player.getUuid() + " - " + player.getName());
        }

        final User user = this.plugin.getUserManager().getIfLoaded(player.getUuid());

        if (user == null) {
            if (!getUniqueConnections().contains(player.getUuid())) {
                this.plugin.getLogger().warn("User " + player.getUuid() + " - " + player.getName() +
                        " doesn't have data pre-loaded, they have never been processed during pre-login in this session." +
                        " - denying login.");
            } else {
                this.plugin.getLogger().warn("User " + player.getUuid() + " - " + player.getName() +
                        " doesn't currently have data pre-loaded, but they have been processed before in this session." +
                        " - denying login.");
            }

            Component reason = TranslationManager.render(Message.LOADING_STATE_ERROR.build(), player.getLocale());
            player.kick(reason);
            return;
        }

        this.plugin.getContextManager().signalContextUpdate(player);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        final Player player = event.getPlayer();
        this.handleDisconnect(player.getUuid());
    }

}
