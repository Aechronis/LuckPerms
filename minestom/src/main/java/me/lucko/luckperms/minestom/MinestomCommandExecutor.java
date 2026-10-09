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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.commodore.file.CommodoreFileReader;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.command.utils.ArgumentTokenizer;
import me.lucko.luckperms.common.sender.Sender;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.jetbrains.annotations.NotNull;

public final class MinestomCommandExecutor extends CommandManager {

    private final @NotNull LuckPermsCommand command;

    private final @NotNull LPMinestomPlugin plugin;
    private final @NotNull CommandRegistry registry;

    public MinestomCommandExecutor(@NotNull LPMinestomPlugin plugin, @NotNull CommandRegistry registry) {
        super(plugin);
        this.plugin = plugin;
        this.registry = registry;
        this.command = new LuckPermsCommand();
    }

    public void register() {
        this.registry.register(this.command);
    }

    public void unregister() {
        this.registry.unregister(this.command);
    }

    private class LuckPermsCommand extends Command {

        public LuckPermsCommand() {
            super("luckperms", "lp", "perm", "perms", "permission", "permissions");

            try (var reader = this.getClass().getResourceAsStream("/luckperms.commodore")) {
                var node = CommodoreFileReader.INSTANCE.parse(reader);
                buildSyntaxes(node, new ArrayList<>());
            } catch (IOException e) {
                throw new RuntimeException("Unable to open luckperms.commodore", e);
            }

            this.setDefaultExecutor((sender, context) -> process(sender, context.getCommandName(), new String[0]));

        }

        private void handler(CommandSender sender, CommandContext context) {
            var params = context.getInput()
                    .replace(context.getCommandName()+" ", "")
                    .split(" ");

            process(sender, context.getCommandName(), params);
        }

        public void process(@NotNull CommandSender sender, @NotNull String command, String @NotNull[] args) {
            List<String> arguments = ArgumentTokenizer.EXECUTE.tokenizeInput(args);

            executeCommand(plugin.getSenderFactory().wrap(sender), command, arguments);
        }

        private void buildSyntaxes(CommandNode<?> node, List<Argument<?>> currentArgs) {
            for (CommandNode<?> child : node.getChildren()) {
                Argument<?> minestomArg = convertArgument(child);

                // Attach the LuckPerms suggestion callback if it's a dynamic argument
                if (!(child instanceof LiteralCommandNode)) {
                    minestomArg.setSuggestionCallback(this::suggest);
                }

                // Create a new list for this specific path
                List<Argument<?>> nextArgs = new ArrayList<>(currentArgs);
                nextArgs.add(minestomArg);

                // Register this path as a valid syntax in Minestom
                this.addSyntax(this::handler, nextArgs.toArray(new Argument[0]));

                // Recurse further down the tree
                buildSyntaxes(child, nextArgs);
            }
        }

        private int argCounter = 0;

        private Argument<?> convertArgument(CommandNode<?> node) {
            if (node instanceof LiteralCommandNode) {
                // Literals MUST keep their exact name, as Minestom uses the ID as the required literal text.
                return net.minestom.server.command.builder.arguments.ArgumentType.Literal(node.getName());
            }

            if (node instanceof ArgumentCommandNode<?, ?> argNode) {
                com.mojang.brigadier.arguments.ArgumentType<?> type = argNode.getType();

                // Append a unique number to the argument name to prevent duplicate key exceptions
                // (e.g., when a literal and an argument share the same name like "user")
                String name = node.getName() + "_" + (argCounter++);

                if (type instanceof StringArgumentType stringType) {
                    if (stringType.getType() == StringArgumentType.StringType.GREEDY_PHRASE) {
                        return net.minestom.server.command.builder.arguments.ArgumentType.StringArray(name);
                    } else {
                        // SINGLE_WORD and QUOTABLE_PHRASE
                        return net.minestom.server.command.builder.arguments.ArgumentType.String(name);
                    }
                } else if (type instanceof IntegerArgumentType intType) {
                    var arg = net.minestom.server.command.builder.arguments.ArgumentType.Integer(name);
                    if (intType.getMinimum() != Integer.MIN_VALUE) arg.min(intType.getMinimum());
                    if (intType.getMaximum() != Integer.MAX_VALUE) arg.max(intType.getMaximum());
                    return arg;
                } else if (type instanceof DoubleArgumentType doubleType) {
                    var arg = net.minestom.server.command.builder.arguments.ArgumentType.Double(name);
                    if (doubleType.getMinimum() != -Double.MAX_VALUE) arg.min(doubleType.getMinimum());
                    if (doubleType.getMaximum() != Double.MAX_VALUE) arg.max(doubleType.getMaximum());
                    return arg;
                } else if (type instanceof FloatArgumentType floatType) {
                    var arg = net.minestom.server.command.builder.arguments.ArgumentType.Float(name);
                    if (floatType.getMinimum() != -Float.MAX_VALUE) arg.min(floatType.getMinimum());
                    if (floatType.getMaximum() != Float.MAX_VALUE) arg.max(floatType.getMaximum());
                    return arg;
                } else if (type instanceof BoolArgumentType) {
                    return net.minestom.server.command.builder.arguments.ArgumentType.Boolean(name);
                }

                // Fallback for unknown argument types
                return net.minestom.server.command.builder.arguments.ArgumentType.String(name);
            }

            // Fallback for unknown node types
            return net.minestom.server.command.builder.arguments.ArgumentType.String(node.getName() + "_" + (argCounter++));
        }

        private void suggest(CommandSender sender, CommandContext context, Suggestion suggestion) {
            Sender wrapped = plugin.getSenderFactory().wrap(sender);
            String input = context.getInput();
            String[] split = input.split(" ", 2);
            String args = split.length > 1 ? split[1] : "";
            List<String> arguments = ArgumentTokenizer.TAB_COMPLETE.tokenizeInput(args);
            tabCompleteCommand(wrapped, arguments).stream().map(SuggestionEntry::new).forEach(suggestion::addEntry);
        }


    }
}
