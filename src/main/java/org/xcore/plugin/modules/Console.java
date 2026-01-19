package org.xcore.plugin.modules;

import arc.func.Cons;
import arc.util.Log;
import arc.Core;
import mindustry.server.ServerControl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.TerminalBuilder;
import org.xcore.plugin.PluginVars;
import reactor.util.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Console {
    private static final ServerControl serverControl = ServerControl.instance;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static LineReader lineReader;

    //@SneakyThrows(IOException.class)
    public static void init() {
        if (!PluginVars.config.consoleEnabled) return;

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader myCustomClassLoader = Console.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(myCustomClassLoader);

            var terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();


            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new StringsCompleter(serverControl.handler.getCommandList().map(c -> c.text)))
                    .build();

            terminal.enterRawMode();
            System.setOut(new BlockingPrintStream(string -> lineReader.printAbove(string)));

            serverControl.serverInput = () -> {
            };

            handleInput();
        } catch (Exception e) {
            Log.err(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static void handleInput() {
        CompletableFuture<String> readFuture = read("> ");

        readFuture.handle((result, exception) -> {
            if (exception != null) {
                Log.err(exception);
                return null;
            }

            if (!result.isEmpty() && !result.startsWith("#")) Core.app.post(() -> serverControl.handleCommandString(result));

            handleInput();
            return null;
        });
    }

    public static CompletableFuture<String> read(String inputPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lineReader.readLine(inputPrompt);
            } catch (UserInterruptException | EndOfFileException err) {
                System.exit(0);
                return null;
            }
        }, executor);
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
        public void write(@NonNull byte[] buf, int off, int len) {
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
