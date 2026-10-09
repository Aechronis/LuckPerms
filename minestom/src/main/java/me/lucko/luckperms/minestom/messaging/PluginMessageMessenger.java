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

package me.lucko.luckperms.minestom.messaging;

import me.lucko.luckperms.common.messaging.pluginmsg.AbstractPluginMessageMessenger;
import net.luckperms.api.messenger.IncomingMessageConsumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.timer.TaskSchedule;

public final class PluginMessageMessenger extends AbstractPluginMessageMessenger {

    private final EventListener<PlayerPluginMessageEvent> listener = EventListener.of(PlayerPluginMessageEvent.class, this::receiveIncomingMessage);

    private final EventNode<? super PlayerPluginMessageEvent> eventNode;

    PluginMessageMessenger(EventNode<? super PlayerPluginMessageEvent> eventNode, IncomingMessageConsumer consumer) {
        super(consumer);
        this.eventNode = eventNode;
    }

    public void init() {
        this.eventNode.addListener(this.listener);
    }

    @Override
    public void close() {
        this.eventNode.removeListener(this.listener);
    }

    @Override
    protected void sendOutgoingMessage(byte[] buf) {
        MinecraftServer.getSchedulerManager().submitTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .findFirst()
                .map(player -> {
                    player.sendPluginMessage(CHANNEL, buf);
                    return TaskSchedule.stop();
                }).orElse(TaskSchedule.tick(100)));
    }

    private void receiveIncomingMessage(PlayerPluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        handleIncomingMessage(event.getMessage());
    }

}
