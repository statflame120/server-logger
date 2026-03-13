package com.archivist.command.commands;

import com.archivist.command.Command;
import com.archivist.fingerprint.GuiFingerprintEngine;

import java.util.function.Consumer;

public class InspectorCommand implements Command {

    @Override public String name() { return "inspector"; }
    @Override public String description() { return "Toggle GUI inspector mode (captures server GUI data)"; }

    @Override
    public void execute(String args, Consumer<String> output) {
        GuiFingerprintEngine engine = GuiFingerprintEngine.getInstance();
        boolean newState = !engine.isInspectorEnabled();
        engine.setInspectorEnabled(newState);
        output.accept("GUI Inspector: " + (newState ? "ON" : "OFF"));
        if (newState) {
            output.accept("Open any server GUI to capture its contents.");
        }
    }
}
