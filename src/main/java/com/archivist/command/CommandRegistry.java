package com.archivist.command;

import com.archivist.command.commands.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Registers and dispatches ! commands for the console.
 */
public class CommandRegistry {

    private static final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        register(new HelpCommand());
        register(new ClearCommand());
        register(new InfoCommand());
        register(new PluginsCommand());
        register(new ScanCommand());
        register(new ExportCommand());
        register(new DbCommand());
        register(new ThemeCommand());
        register(new ProbeCommand());
        register(new InspectorCommand());
        register(new ReplayFingerprintsCommand());
    }

    public static void register(Command cmd) {
        commands.put(cmd.name().toLowerCase(Locale.ROOT), cmd);
    }

    /**
     * Dispatch a command string (with or without "!" prefix).
     * @param input  full input string (e.g., "export json")
     * @param output callback to print response lines
     * @return true if a command was found and executed
     */
    public static boolean dispatch(String input, Consumer<String> output) {
        if (input == null) return false;
        // Accept commands with or without "!" prefix
        String stripped = input.startsWith("!") ? input.substring(1).trim() : input.trim();
        if (stripped.isEmpty()) return false;

        String[] parts = stripped.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1] : "";

        Command cmd = commands.get(cmdName);
        if (cmd == null) {
            output.accept("Unknown command: " + cmdName + ". Type help for a list.");
            return true;
        }

        try {
            cmd.execute(args, output);
        } catch (Exception e) {
            output.accept("Error executing " + cmdName + ": " + e.getMessage());
        }
        return true;
    }

    /** Get all registered commands (for help listing). */
    public static Collection<Command> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /** Get command completions for the given input prefix (for tab-complete). */
    public static List<String> getCompletions(String input) {
        if (input == null) return Collections.emptyList();
        String prefix = (input.startsWith("!") ? input.substring(1) : input).toLowerCase(Locale.ROOT).trim();
        List<String> matches = new ArrayList<>();
        for (String name : commands.keySet()) {
            if (name.startsWith(prefix)) {
                matches.add(name);
            }
        }
        return matches;
    }
}
