package org.xcore.plugin.integration;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.server.ServerControl;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.service.BundleService;

import java.util.List;
import java.util.Set;

@Singleton
public class CloudJLineCompleter implements Completer {

    private final CloudService cloudService;
    private final BundleService bundleService;
    private XCoreSender consoleSender;

    @Inject
    public CloudJLineCompleter(CloudService cloudService, BundleService bundleService) {
        this.cloudService = cloudService;
        this.bundleService = bundleService;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (consoleSender == null) {
            consoleSender = new XCoreSender(new MindustrySender.ConsoleSender(), bundleService);
        }

        try {
            Set<String> added = new java.util.HashSet<>();

            var suggestions = cloudService.getServerManager().suggestionFactory()
                    .suggestImmediately(consoleSender, line.line());

            for (var s : suggestions.list()) {
                candidates.add(new Candidate(s.suggestion()));
                added.add(s.suggestion());
            }

            if (line.wordIndex() == 0) {
                String currentWord = line.word().toLowerCase();

                ServerControl.instance.handler.getCommandList().forEach(cmd -> {
                    if (cmd.text.toLowerCase().startsWith(currentWord) && !added.contains(cmd.text)) {
                        candidates.add(new Candidate(cmd.text));
                    }
                });
            }

        } catch (IllegalStateException e) {
            if ("Expected access requirements to be propagated".equals(e.getMessage())) {
                return;
            }
            Log.err("Completion state error", e);
        } catch (Exception err) {
            Log.err("Completion error", err);
        }
    }
}
