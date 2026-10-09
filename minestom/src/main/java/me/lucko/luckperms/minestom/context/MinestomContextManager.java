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

package me.lucko.luckperms.minestom.context;

import java.util.Objects;
import java.util.UUID;
import me.lucko.luckperms.common.context.manager.DetachedContextManager;
import me.lucko.luckperms.common.context.manager.QueryOptionsSupplier;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jspecify.annotations.Nullable;

public final class MinestomContextManager extends DetachedContextManager<Player, Player> {

    private static final Tag<QueryOptionsSupplier> QUERY_OPTIONS_SUPPLIER_TAG = Tag.Transient("luckperms_query_options_supplier");

    public MinestomContextManager(LuckPermsPlugin plugin) {
        super(plugin, Player.class, Player.class);
    }

    @Override
    public UUID getUniqueId(Player player) {
        return player.getUuid();
    }

    @Override
    public @Nullable QueryOptionsSupplier getQueryOptionsSupplier(Player subject) {
        Objects.requireNonNull(subject);
        QueryOptionsSupplier supplier = subject.getTag(QUERY_OPTIONS_SUPPLIER_TAG);
        if (supplier == null) {
            supplier = this.createQueryOptionsSupplier(subject);
            subject.setTag(QUERY_OPTIONS_SUPPLIER_TAG, supplier);
        }
        return supplier;
    }

    public void clearQueryOptionsSupplier(Player subject) {
        subject.removeTag(QUERY_OPTIONS_SUPPLIER_TAG);
    }

}
