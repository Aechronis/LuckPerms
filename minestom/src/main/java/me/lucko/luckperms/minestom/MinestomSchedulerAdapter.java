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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;
import me.lucko.luckperms.common.plugin.scheduler.SchedulerTask;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.util.Iterators;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;

public final class MinestomSchedulerAdapter implements SchedulerAdapter {

    private final Executor executor;
    private final Set<Task> tasks = Collections.newSetFromMap(new WeakHashMap<>());

    public MinestomSchedulerAdapter(LPMinestomBootstrap plugin) {
        this.executor = r -> {
            var t = new Thread(r);
            t.setName("LPMinestom Thread");
            t.start();
        };
    }

    @Override
    public void executeAsync(Runnable task) {
        this.executor.execute(task);
    }

    @Override
    public void executeSync(Sender ctx, Runnable task) {
        MinecraftServer.getSchedulerManager().scheduleNextProcess(task);
    }

    @Override
    public Executor async() {
        return this.executor;
    }

    @Override
    public SchedulerTask asyncLater(Runnable task, long delay, TimeUnit unit) {
        var t = MinecraftServer.getSchedulerManager()
                .buildTask(task)
                .delay(delay, unit.toChronoUnit())
                .schedule();
        this.tasks.add(t);
        return t::cancel;
    }

    @Override
    public SchedulerTask asyncRepeating(Runnable task, long interval, TimeUnit unit) {
        var t = MinecraftServer.getSchedulerManager()
                .buildTask(task)
                .repeat(interval, unit.toChronoUnit())
                .schedule();
        this.tasks.add(t);
        return t::cancel;
    }

    @Override
    public void shutdownScheduler() {
        Iterators.tryIterate(this.tasks, Task::cancel);
    }

    @Override
    public void shutdownExecutor() {

    }
}
