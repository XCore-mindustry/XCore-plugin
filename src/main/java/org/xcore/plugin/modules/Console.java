package org.xcore.plugin.modules;

import arc.Core;
import arc.func.Cons;
import mindustry.server.ServerControl;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.XcorePlugin;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class Console {
    public static ServerControl serverControl;
    public static LineReader lineReader;

    /**
     * Инициализация улучшения консольного ввода
     */
    public static void init() {
        if (!PluginVars.config.consoleEnabled) return;

        serverControl = (ServerControl) Core.app.getListeners().find(listener -> listener instanceof ServerControl);

        try {
            lineReader = LineReaderBuilder.builder().build();

            System.setOut(new BlockingPrintStream(string -> lineReader.printAbove(string)));
        } catch (Exception e) {
            XcorePlugin.err(String.valueOf(e));
            XcorePlugin.err("Exiting...");
            Core.app.exit();
        }

        serverControl.serverInput = () -> {
            while (true) {
                try {
                    String line = lineReader.readLine("> ");
                    if (!line.isEmpty()) {
                        Core.app.post(() -> serverControl.handleCommandString(line));
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    Core.app.exit();
                    System.exit(0);
                }
            }
        };
    }

    public static class BlockingPrintStream extends PrintStream {
        private final Cons<String> cons;

        private int last = -1;

        public BlockingPrintStream(Cons<String> cons) {
            super(new ByteArrayOutputStream());
            this.cons = cons;
        }

        public ByteArrayOutputStream out() {
            return (ByteArrayOutputStream) out;
        }

        @Override
        public void write(int b) {
            if (last == 13 && b == 10) {
                last = -1;
                return;
            }

            last = b;
            if (b == 13 || b == 10) {
                flush();
            } else {
                super.write(b);
            }
        }

        @Override
        public void write(@NotNull byte[] buf, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(buf[off + i]);
            }
        }

        @Override
        public void flush() {
            String str = out().toString();
            out().reset();
            cons.get(str);
        }
    }
}
