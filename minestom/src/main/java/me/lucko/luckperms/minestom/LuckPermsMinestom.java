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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.config.generic.adapter.EnvironmentVariableConfigAdapter;
import me.lucko.luckperms.minestom.context.ContextProvider;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minestom.server.command.builder.Command;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LuckPermsMinestom {

    private static LPMinestomBootstrap bootstrap = null;

    private LuckPermsMinestom() {}

    public static @NotNull Builder builder(@NotNull Path dataDirectory) {
        if (bootstrap != null) throw new RuntimeException("Cannot initialize LuckPerms Minestom - it is already initialized!");
        return new BuilderImpl(dataDirectory.toAbsolutePath());
    }

    public static void disable() {
        if (bootstrap == null) throw new RuntimeException("Cannot disable LuckPerms Minestom - it is not initialized!");
        bootstrap.onDisable();
        bootstrap = null;
    }

    public interface Builder {

        /**
         * Sets whether the LuckPerms commands should be registered
         *
         * @param enabled if the commands should be registered
         * @return the builder instance
         * @deprecated use {@link Builder#commandRegistry(CommandRegistry)}
         */
        @Deprecated
        @Contract("_ -> this")
        default @NotNull Builder commands(boolean enabled) {
            return enabled ? this.commandRegistry(CommandRegistry.minestom()) : this.commandRegistry(null);
        }

        /**
         * Sets the command registry handler where LuckPerms commands
         * should be registered. Provided constructors are:
         * <ul>
         * <li>{@link Builder#commandRegistry(Consumer, Consumer)}</li>
         * <li>{@link CommandRegistry#of(Consumer, Consumer)}</li>
         * <li>{@link CommandRegistry#minestom()}</li>
         * </ul>
         *
         * @param handler the command registry handler
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder commandRegistry(@Nullable CommandRegistry handler);

        /**
         * Sets the command registry handler where LuckPerms commands
         * should be registered.
         */
        @Contract("_, _ -> this")
        default @NotNull Builder commandRegistry(
                @NotNull Consumer<Command> register,
                @NotNull Consumer<Command> unregister
        ) {
            return this.commandRegistry(CommandRegistry.of(register, unregister));
        }

        /**
         * Adds a context provider to the platform
         *
         * @param provider the provider to add
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder contextProvider(@NotNull ContextProvider provider);

        /**
         * Adds a collection of context providers to the platform
         *
         * @param providers the providers to add
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder contextProviders(@NotNull ContextProvider... providers);

        /**
         * Adds a collection of context providers to the platform
         *
         * @param providers the providers to add
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder contextProviders(@NotNull Iterable<ContextProvider> providers);


        /**
         * Suggests a permission to be registered with the platform
         *
         * @param permission the permission to suggest
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder permissionSuggestion(@NotNull String permission);

        /**
         * Suggests a collection of permissions to be registered with the platform
         *
         * @param permissions the permissions to suggest
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder permissionSuggestions(@NotNull String... permissions);

        /**
         * Suggests a collection of permissions to be registered with the platform
         *
         * @param permissions the permissions to suggest
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder permissionSuggestions(@NotNull Iterable<String> permissions);


        /**
         * Sets the configuration adapter to use. Provided options are:
         * <ul>
         * <li>{@link me.lucko.luckperms.common.config.generic.adapter.EnvironmentVariableConfigAdapter}</li>
         * <li>{@link me.lucko.luckperms.common.config.generic.adapter.MultiConfigurationAdapter}</li>
         * <li>{@link me.lucko.luckperms.common.config.generic.adapter.SystemPropertyConfigAdapter}</li>
         * <li>{@link me.lucko.luckperms.common.config.generic.adapter.ConfigurateConfigAdapter}</li>
         * </ul>
         *
         * @param adapter the adapter to use
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder configurationAdapter(@NotNull Function<LPMinestomPlugin, ConfigurationAdapter> adapter);


        /**
         * Sets the logger to use
         *
         * @param logger the logger to use
         * @return the builder instance
         */
        @Contract("_ -> this")
        @NotNull Builder logger(@NotNull Logger logger);


        /**
         * Enables LuckPerms
         *
         * @return the LuckPerms instance
         */
        @NotNull LuckPerms enable();

    }

    private static class BuilderImpl implements Builder {

        private final @NotNull Set<ContextProvider> contextProviders = new HashSet<>();
        private final @NotNull Set<String> permissionSuggestions = new HashSet<>();

        private final Path dataDirectory;
        private @Nullable CommandRegistry commandRegistry;
        private @NotNull Function<LPMinestomPlugin, ConfigurationAdapter> configurationAdapter = EnvironmentVariableConfigAdapter::new;
        private @NotNull Logger logger = LoggerFactory.getLogger(LuckPermsMinestom.class);

        private BuilderImpl(@NotNull Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        @Override
        public @NotNull Builder commandRegistry(@Nullable CommandRegistry handler) {
            this.commandRegistry = handler;
            return this;
        }

        @Override
        public @NotNull Builder contextProvider(@NotNull ContextProvider provider) {
            this.contextProviders.add(provider);
            return this;
        }

        @Override
        public @NotNull Builder contextProviders(@NotNull ContextProvider... providers) {
            return this.contextProviders(Arrays.asList(providers));
        }

        @Override
        public @NotNull Builder contextProviders(@NotNull Iterable<ContextProvider> providers) {
            providers.forEach(this.contextProviders::add);
            return this;
        }

        @Override
        public @NotNull Builder permissionSuggestion(@NotNull String permission) {
            this.permissionSuggestions.add(permission);
            return this;
        }

        @Override
        public @NotNull Builder permissionSuggestions(@NotNull String... permissions) {
            return this.permissionSuggestions(Arrays.asList(permissions));
        }

        @Override
        public @NotNull Builder permissionSuggestions(@NotNull Iterable<String> permissions) {
            permissions.forEach(this.permissionSuggestions::add);
            return this;
        }

        @Override
        public @NotNull Builder configurationAdapter(@NotNull Function<LPMinestomPlugin, ConfigurationAdapter> adapter) {
            this.configurationAdapter = adapter;
            return this;
        }

        @Override
        public @NotNull Builder logger(@NotNull Logger logger) {
            this.logger = logger;
            return this;
        }

        @Override
        public @NotNull LuckPerms enable() {
            bootstrap = new LPMinestomBootstrap(
                    this.logger,
                    this.dataDirectory,
                    this.contextProviders,
                    this.configurationAdapter,
                    this.permissionSuggestions,
                    this.commandRegistry
            );
            bootstrap.onEnable();
            return LuckPermsProvider.get();
        }

    }

}
