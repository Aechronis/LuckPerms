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

package me.lucko.luckperms.minestom.init;

import com.google.common.base.Splitter;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ModernConfigurateConfigAdapter implements ConfigurationAdapter {

    private final LuckPermsPlugin plugin;
    private final Path path;
    private ConfigurationNode root;

    public ModernConfigurateConfigAdapter(LuckPermsPlugin plugin, Path path) {
        this.plugin = plugin;
        this.path = path;
        this.reload();
    }

    protected abstract ConfigurationLoader<? extends @NotNull ConfigurationNode> createLoader(Path path);

    @Override
    public void reload() {
        ConfigurationLoader<? extends @NotNull ConfigurationNode> loader = this.createLoader(this.path);
        try {
            this.root = loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ConfigurationNode resolvePath(String path) {
        if (this.root == null) {
            throw new RuntimeException("Config is not loaded.");
        }

        return this.root.node(Splitter.on('.').splitToList(path).toArray());
    }

    @Override
    public String getString(String path, String def) {
        String string = this.resolvePath(path).getString();
        if (string == null) return def;
        return string;
    }

    @Override
    public int getInteger(String path, int def) {
        return this.resolvePath(path).getInt(def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return this.resolvePath(path).getBoolean(def);
    }

    @Override
    public List<String> getStringList(String path, List<String> def) {
        ConfigurationNode node = this.resolvePath(path);
        if (node.virtual() || !node.isList()) {
            return def;
        }

        try {
            return node.getList(String.class, def);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, String> getStringMap(String path, Map<String, String> def) {
        ConfigurationNode node = this.resolvePath(path);
        if (node.virtual()) {
            return def;
        }

        return node.childrenMap().entrySet().stream().collect(Collectors.toMap(
                k -> k.getKey().toString(),
                v -> v.getValue().toString()
        ));
    }

    @Override
    public LuckPermsPlugin getPlugin() {
        return this.plugin;
    }

}
