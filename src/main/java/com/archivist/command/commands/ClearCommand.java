package com.archivist.command.commands;

import com.archivist.command.Command;
import com.archivist.data.EventBus;

import java.util.function.Consumer;

public class ClearCommand implements Command {

    @Override public String name() { return "clear"; }
    @Override public String description() { return "Clear console output"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        EventBus.clearEvents();
        output.accept("Console cleared.");
    }
}
