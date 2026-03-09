package com.archivist.command.commands;

import com.archivist.command.Command;
import com.archivist.command.CommandRegistry;

import java.util.function.Consumer;

public class HelpCommand implements Command {

    @Override public String name() { return "help"; }
    @Override public String description() { return "List all available commands"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        output.accept("=== Archivist Commands ===");
        for (Command cmd : CommandRegistry.getCommands()) {
            output.accept("  !" + cmd.name() + " - " + cmd.description());
        }
    }
}
