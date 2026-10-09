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

package me.lucko.luckperms.minestom;

import me.lucko.luckperms.minestom.context.defaults.DimensionTypeContextProvider;
import me.lucko.luckperms.minestom.init.HoconConfigurationAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.util.Tristate;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandResult;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.AsyncEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises real Minestom and LuckPerms together without opening a listening socket. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MinestomCompatibilityTest {

    @TempDir
    Path dataDirectory;

    private LPMinestomPlugin plugin;
    private boolean enabled;
    private final List<Command> registered = new ArrayList<>();
    private final List<Command> unregistered = new ArrayList<>();
    private Set<EventNode<Event>> initialNodes;

    @BeforeEach
    void initializeServer() throws Exception {
        System.setProperty("minestom.shutdown-on-signal", "false");
        MinecraftServer.init(new Auth.Offline());
        this.initialNodes = Set.copyOf(MinecraftServer.getGlobalEventHandler().getChildren());
        Files.writeString(this.dataDirectory.resolve("luckperms.conf"), """
                storage-method = "h2"
                messaging-service = "none"
                auto-install-translations = false
                watch-files = false
                sync-minutes = -1
                """);
    }

    @AfterEach
    void shutdownServer() {
        try {
            if (this.enabled) {
                disable();
            }
        } finally {
            MinecraftServer.stopCleanly();
        }
    }

    @Test
    void commandsAndPermissionsSurviveDisableAndReenable() throws Exception {
        LuckPerms api = enable(true);
        assertSame(api, LuckPermsProvider.get());
        assertEquals(1, this.registered.size());
        Command command = this.registered.getFirst();
        for (String alias : List.of("luckperms", "lp", "perm", "perms", "permission", "permissions")) {
            assertSame(command, MinecraftServer.getCommandManager().getCommand(alias));
        }

        // These calls used to link against getOnlinePlayers(): Collection, now returning Set.
        assertTrue(this.plugin.getBootstrap().getOnlinePlayers().isEmpty());
        assertTrue(this.plugin.getBootstrap().getPlayerList().isEmpty());
        assertEquals(1, this.plugin.getOnlineSenders().count());
        assertNotNull(this.plugin.getPermissionRegistry().getRootNode());

        CapturingConsole console = new CapturingConsole();
        assertEquals(CommandResult.Type.SUCCESS,
                MinecraftServer.getCommandManager().execute(console, "lp").getType());
        String banner = console.messages.poll(10, TimeUnit.SECONDS);
        String translatedPrompt = console.messages.poll(10, TimeUnit.SECONDS);
        assertNotNull(banner, "The registered Minestom command should execute");
        assertTrue(banner.contains("LuckPerms"), banner);
        assertNotNull(translatedPrompt, "Adventure should render the command translation");
        assertFalse(translatedPrompt.contains("luckperms."), translatedPrompt);
        assertTrue(translatedPrompt.contains("lp"), translatedPrompt);

        UUID uuid = UUID.randomUUID();
        User user = api.getUserManager().loadUser(uuid, "PersistenceTest").get(10, TimeUnit.SECONDS);
        user.data().add(Node.builder("smoke.persisted").build());
        user.data().add(Node.builder("smoke.denied").value(false).build());
        api.getUserManager().saveUser(user).get(10, TimeUnit.SECONDS);
        assertEquals(Tristate.TRUE, user.getCachedData().getPermissionData().checkPermission("smoke.persisted"));
        assertEquals(Tristate.FALSE, user.getCachedData().getPermissionData().checkPermission("smoke.denied"));

        disable();
        assertEquals(this.registered, this.unregistered);
        assertNull(MinecraftServer.getCommandManager().getCommand("lp"));
        assertEquals(this.initialNodes, Set.copyOf(MinecraftServer.getGlobalEventHandler().getChildren()));
        assertThrows(IllegalStateException.class, LuckPermsProvider::get);

        LuckPerms reloaded = enable(false);
        assertSame(reloaded, LuckPermsProvider.get());
        assertEquals(1, this.registered.size(), "Disabling commands must avoid platform registration");
        assertNull(MinecraftServer.getCommandManager().getCommand("lp"));
        User restored = reloaded.getUserManager().loadUser(uuid, "PersistenceTest").get(10, TimeUnit.SECONDS);
        assertEquals(Tristate.TRUE, restored.getCachedData().getPermissionData().checkPermission("smoke.persisted"));
        assertEquals(Tristate.FALSE, restored.getCachedData().getPermissionData().checkPermission("smoke.denied"));

        disable();
        assertEquals(this.initialNodes, Set.copyOf(MinecraftServer.getGlobalEventHandler().getChildren()));
        assertThrows(IllegalStateException.class, LuckPermsProvider::get);
    }

    @Test
    void syntheticLoginLoadsUserAndDimensionContextsAffectPermissions() throws Exception {
        LuckPerms api = enable(false);
        TestConnection connection = new TestConnection();
        connection.setClientState(ConnectionState.LOGIN);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "ContextTest");
        dispatchAsyncEvent(new AsyncPlayerPreLoginEvent(
                connection, profile, connection.loginPluginMessageProcessor()));
        assertNotNull(api.getUserManager().getUser(profile.uuid()), "Pre-login must finish loading user data");

        TestPlayer player = new TestPlayer(connection, profile);
        connection.setPlayer(player);
        dispatchAsyncEvent(new AsyncPlayerConfigurationEvent(player, true));
        assertTrue(connection.isOnline(), "A successfully loaded user must pass configuration");

        DimensionTypeContextProvider dimensions = new DimensionTypeContextProvider();
        assertTrue(dimensions.query(player).isEmpty());
        assertTrue(dimensions.potentialValues().containsAll(Set.of("minecraft:overworld", "minecraft:the_nether")));

        PlayerAdapter<Player> adapter = api.getPlayerAdapter(Player.class);
        adapter.getUser(player).data().add(Node.builder("smoke.dimension")
                .withContext("dimension-type", "minecraft:the_nether").build());

        Instance overworld = MinecraftServer.getInstanceManager().createInstanceContainer(DimensionType.OVERWORLD);
        Instance nether = MinecraftServer.getInstanceManager().createInstanceContainer(DimensionType.THE_NETHER);
        player.contextInstance = overworld;
        MinecraftServer.getGlobalEventHandler().call(new PlayerSpawnEvent(player, overworld, true));
        assertEquals("minecraft:overworld", dimensions.query(player).orElseThrow());
        assertFalse(adapter.getPermissionData(player).checkPermission("smoke.dimension").asBoolean());

        player.contextInstance = nether;
        MinecraftServer.getGlobalEventHandler().call(new PlayerSpawnEvent(player, nether, false));
        assertEquals("minecraft:the_nether", dimensions.query(player).orElseThrow());
        assertTrue(adapter.getContext(player).contains("dimension-type", "minecraft:the_nether"));
        assertEquals(Tristate.TRUE, adapter.getPermissionData(player).checkPermission("smoke.dimension"));

        MinecraftServer.getGlobalEventHandler().call(new PlayerDisconnectEvent(player));
        connection.disconnect();
    }

    private static void dispatchAsyncEvent(AsyncEvent event) throws Exception {
        FutureTask<Void> dispatch = new FutureTask<>(() -> MinecraftServer.getGlobalEventHandler().call(event), null);
        Thread.startVirtualThread(dispatch);
        try {
            dispatch.get(10, TimeUnit.SECONDS);
        } finally {
            dispatch.cancel(true);
        }
    }

    private LuckPerms enable(boolean commands) {
        LuckPermsMinestom.Builder builder = LuckPermsMinestom.builder(this.dataDirectory)
                .configurationAdapter(plugin -> {
                    this.plugin = plugin;
                    return new HoconConfigurationAdapter(plugin);
                });
        if (commands) {
            builder.commandRegistry(command -> {
                this.registered.add(command);
                MinecraftServer.getCommandManager().register(command);
            }, command -> {
                this.unregistered.add(command);
                MinecraftServer.getCommandManager().unregister(command);
            });
        }
        LuckPerms api = builder.enable();
        this.enabled = true;
        return api;
    }

    private void disable() {
        LuckPermsMinestom.disable();
        this.enabled = false;
    }

    private static final class CapturingConsole extends ConsoleSender {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void sendMessage(Component message) {
            this.messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
    }

    private static final class TestConnection extends PlayerConnection {
        @Override
        public void sendPacket(SendablePacket packet) {
            // The smoke test drives lifecycle events without a network client.
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 0);
        }
    }

    private static final class TestPlayer extends Player {
        private Instance contextInstance;

        private TestPlayer(PlayerConnection connection, GameProfile profile) {
            super(connection, profile);
        }

        @Override
        public Instance getInstance() {
            return this.contextInstance;
        }
    }
}
