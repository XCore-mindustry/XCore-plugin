package org.xcore.plugin.config;

import arc.files.Fi;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline entrypoint exposing the plugin's config parser to development tooling. */
public final class ConfigValidationCli {
    private ConfigValidationCli() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream output, PrintStream errors) {
        if (args.length != 2) {
            errors.println("Usage: ConfigValidationCli <xcore|secrets|sentinel|resolve-secrets> <path>");
            return 2;
        }
        try {
            if ("resolve-secrets".equals(args[0])) {
                output.println(resolveSecrets(Path.of(args[1])));
            } else {
                validate(args[0], Path.of(args[1]));
            }
            return 0;
        } catch (Exception | LinkageError failure) {
            // Binding exceptions may quote credentials from the input. Never emit their messages.
            errors.println("Configuration validation failed. Check field types, required settings and installed plugin JARs.");
            return 1;
        }
    }

    static Path resolveSecrets(Path dataDirectory) {
        var config = ConfigTomlLoader.readXcoreToml(Fi.get(dataDirectory.resolve("xcore.toml").toString()));
        return ConfigTomlLoader.resolveSecretsToml(config.paths.globalConfigDirectory).file().toPath().toAbsolutePath();
    }

    static void validate(String name, Path path) throws IOException, ClassNotFoundException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configuration file is missing");
        }
        var file = Fi.get(path.toString());
        switch (name) {
            case "xcore" -> ConfigTomlLoader.readXcoreToml(file);
            case "secrets" -> {
                var config = PluginConfigLoader.lenientTomlMapper().readValue(path.toFile(), TomlSecretsConfig.class);
                config.normalize();
                config.validate(file);
            }
            case "sentinel" -> {
                // Sentinel is optional and owns its DTO; it is loaded only when explicitly requested.
                Class<?> type = Class.forName("org.xcore.plugin.xcoresentinel.config.SentinelConfig");
                Object config = PluginConfigLoader.lenientTomlMapper().readValue(path.toFile(), type);
                if (config instanceof SelfNormalizing normalizing) {
                    normalizing.normalize();
                }
            }
            default -> throw new IllegalArgumentException("Unknown config name");
        }
    }
}
