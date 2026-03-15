package com.archivist.command;

import java.util.function.Consumer;

/**
 * Base interface for all console commands.
 * Commands receive a string of arguments and a callback to output text.
 */
public interface Command {

    /** The command name. */
    String name();

    /** Short description shown in help output. */
    String description();

    /**
     * Execute the command.
     * @param args  arguments string (everything after the command name)
     * @param output callback to print lines to the console output
     */
    void execute(String args, Consumer<String> output);
}
