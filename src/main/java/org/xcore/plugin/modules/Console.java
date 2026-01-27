package org.xcore.plugin.modules;

import arc.func.Cons;
import arc.util.Log;
import arc.Core;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.server.ServerControl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class Console {

    private final Config config;
    private final ServerControl serverControl;

    private ExecutorService executor;
    private LineReader lineReader;
    private Terminal terminal;
    private volatile boolean running = false;

    @Inject
    public Console(Config config) {
        this.config = config;
        this.serverControl = ServerControl.instance;
    }

    @PostConstruct
    public void init() {
        if (!config.consoleEnabled) {
            return;
        }

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader customClassLoader = Console.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(customClassLoader);

            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new StringsCompleter(serverControl.handler.getCommandList().map(c -> c.text)))
                    .build();

            terminal.enterRawMode();
            System.setOut(new BlockingPrintStream(string -> lineReader.printAbove(string)));

            serverControl.serverInput = () -> {};

            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Console-Input-Thread"));

            running = true;
            handleNextInput();
        } catch (Exception e) {
            Log.err("Failed to initialize console", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void handleNextInput() {
        if (!running) {
            return;
        }

        readLine("> ").handle((result, exception) -> {
            if (exception != null) {
                if (!(exception.getCause() instanceof UserInterruptException) &&
                        !(exception.getCause() instanceof EndOfFileException)) {
                    Log.err("Console read error", exception);
                }
                return null;
            }

            if (result != null && !result.isEmpty() && !result.startsWith("#")) {
                Core.app.post(() -> serverControl.handleCommandString(result));
            }

            handleNextInput();
            return null;
        });
    }

    private CompletableFuture<String> readLine(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lineReader.readLine(prompt);
            } catch (UserInterruptException | EndOfFileException e) {
                Log.info("Console input terminated");
                System.exit(0);
                return null;
            }
        }, executor);
    }

    @PreDestroy
    public void shutdown() {
        running = false;

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception e) {
                Log.err("Failed to close terminal", e);
            }
        }

        Log.info("Console shutdown complete");
    }

    public static class BlockingPrintStream extends PrintStream {
        private final Cons<String> outputHandler;
        private int lastChar = -1;

        public BlockingPrintStream(Cons<String> outputHandler) {
            super(new ByteArrayOutputStream());
            this.outputHandler = outputHandler;
        }

        private ByteArrayOutputStream buffer() {
            return (ByteArrayOutputStream) out;
        }

        @Override
        public void write(int b) {
            if (lastChar == 13 && b == 10) {
                lastChar = -1;
                return;
            }

            lastChar = b;

            if (b == 13 || b == 10) {
                flush();
            } else {
                super.write(b);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(buf[off + i]);
            }
        }

        @Override
        public void flush() {
            String content = buffer().toString();
            buffer().reset();
            outputHandler.get(content);
        }
    }
}
