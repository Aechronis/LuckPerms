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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import me.lucko.luckperms.common.config.generic.adapter.EnvironmentVariableConfigAdapter;
import me.lucko.luckperms.common.config.generic.adapter.MultiConfigurationAdapter;
import me.lucko.luckperms.minestom.init.DummyContextProvider;
import me.lucko.luckperms.minestom.init.HoconConfigurationAdapter;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.node.Node;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionManager;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinestomServer {

    private static final Logger logger = LoggerFactory.getLogger(MinestomServer.class);

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init(new Auth.Online());
        MinecraftServer.setBrandName("LU15");

        logger.info("Starting LP");
        // initialize LuckPerms
        Path directory = Path.of("luckperms");
        LuckPerms luckPerms = LuckPermsMinestom.builder(directory)
                .commandRegistry(CommandRegistry.minestom())
                .contextProvider(new DummyContextProvider())
                .configurationAdapter(plugin -> new MultiConfigurationAdapter(plugin,
                        new EnvironmentVariableConfigAdapter(plugin),
                        new HoconConfigurationAdapter(plugin)
                )).permissionSuggestions("test.permission", "test.other")
                .enable();


        logger.info("Starting world");
        // set up Minestom
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        EventNode<Event> eventNode = MinecraftServer.getGlobalEventHandler();
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(new Pos(0, 41, 0));
        });

        // register shutdown hook to delete the temp directory
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            try {
                LuckPermsMinestom.disable();
                Files.deleteIfExists(directory);
            } catch (IOException ignored) {
                // oh well...
            }
        });

        OpenToLAN.open();

        startConsoleReader();

        server.start("0.0.0.0", 25565);
        logger.info("Server up");
    }

    private static void startConsoleReader() {
        var t = new Thread(() -> {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            var cmdManager = MinecraftServer.getCommandManager();
            var sender = cmdManager.getConsoleSender();

            while (true) {
                String line;
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    logger.error("Unable to read line from console, skipping:", e);
                    break;
                }

                var clean = line.trim();
                if (clean.isEmpty()) continue;

                cmdManager.execute(sender, clean);
            }
        });
        t.setDaemon(true);
        t.start();
    }

}
