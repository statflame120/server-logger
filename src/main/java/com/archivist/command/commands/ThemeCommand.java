package com.archivist.command.commands;

import com.archivist.command.Command;
import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.ThemeManager;

import java.util.Map;
import java.util.function.Consumer;

public class ThemeCommand implements Command {

    @Override public String name() { return "theme"; }
    @Override public String description() { return "Switch color theme (!theme <name>)"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        String name = args.trim().toLowerCase();

        Map<String, ColorScheme> themes = getThemes();
        if (name.isEmpty()) {
            output.accept("Current theme: " + ColorScheme.get().name());
            output.accept("Available: " + String.join(", ", themes.keySet()));
            return;
        }

        ColorScheme theme = themes.get(name);
        if (theme == null) {
            output.accept("Unknown theme: " + name);
            output.accept("Available: " + String.join(", ", themes.keySet()));
            return;
        }

        ColorScheme.setActive(theme);
        output.accept("Theme changed to: " + theme.name());
    }

    /** Get all registered themes (delegates to ThemeManager). */
    public static Map<String, ColorScheme> getThemes() {
        return ThemeManager.getInstance().getThemes();
    }
}
