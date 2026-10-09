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

import java.util.function.Consumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import org.jetbrains.annotations.NotNull;

public interface CommandRegistry {

    static @NotNull CommandRegistry of(
            @NotNull Consumer<Command> register,
            @NotNull Consumer<Command> unregister
    ) {
        return new CommandRegistry() {
            @Override
            public void register(@NotNull Command command) {
                register.accept(command);
            }

            @Override
            public void unregister(@NotNull Command command) {
                unregister.accept(command);
            }
        };
    }

    static @NotNull CommandRegistry minestom() {
        return new CommandRegistry() {
            @Override
            public void register(@NotNull Command command) {
                MinecraftServer.getCommandManager().register(command);
            }

            @Override
            public void unregister(@NotNull Command command) {
                MinecraftServer.getCommandManager().unregister(command);
            }
        };
    }

    void register(@NotNull Command command);

    void unregister(@NotNull Command command);

}
