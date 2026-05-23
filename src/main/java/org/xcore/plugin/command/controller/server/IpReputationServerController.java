package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationAllowlist;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationResult;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationService;

import java.util.Set;

/**
 * Server console commands for IP reputation diagnostics and allowlist management.
 * <p>
 * All commands are server-only and log results to the console.
 */
@Singleton
public class IpReputationServerController implements CloudServerController {

    private final IpReputationService ipReputationService;
    private final IpReputationAllowlist allowlist;

    /**
     * Creates an IpReputationServerController and stores the provided service and allowlist for use by its command handlers.
     */
    @Inject
    public IpReputationServerController(IpReputationService ipReputationService,
                                        IpReputationAllowlist allowlist) {
        this.ipReputationService = ipReputationService;
        this.allowlist = allowlist;
    }

    /**
     * Perform a fresh provider reputation lookup for the specified IP and log the resulting reputation fields.
     *
     * @param ip the IP address to look up; leading and trailing whitespace is ignored
     */
    @Command("iprep lookup <ip>")
    @CommandDescription("Performs a fresh provider lookup for the given IP.")
    public void lookup(XCoreSender sender,
                       @Argument(value = "ip", description = "IP address to look up") String ip) {
        if (ip == null || ip.isBlank()) {
            Log.err("IP address cannot be blank.");
            return;
        }

        IpReputationResult result = ipReputationService.lookup(ip.trim());
        if (result == null) {
            Log.err("Lookup failed or IP reputation is disabled.");
            return;
        }

        Log.info("IP Reputation for @: proxy=@, hosting=@, mobile=@",
                result.ip(), result.proxy(), result.hosting(), result.mobile());
    }

    /**
     * Determines whether a given IP would be blocked by the current IP-reputation policy.
     *
     * @param sender the command sender invoking this check
     * @param ip the IP address to evaluate; must not be null or blank
     */
    @Command("iprep check <ip>")
    @CommandDescription("Checks whether the given IP would be blocked by current policy.")
    public void check(XCoreSender sender,
                      @Argument(value = "ip", description = "IP address to check") String ip) {
        if (ip == null || ip.isBlank()) {
            Log.err("IP address cannot be blank.");
            return;
        }

        boolean blocked = ipReputationService.isBlocked(ip.trim());
        if (blocked) {
            Log.info("IP @ would be BLOCKED.", ip.trim());
        } else {
            Log.info("IP @ would be ALLOWED.", ip.trim());
        }
    }

    /**
     * Adds the given IP address to the IP reputation allowlist.
     *
     * If `ip` is null or blank the method logs an error and returns without modifying the allowlist.
     *
     * @param ip the IP address to add; leading and trailing whitespace are trimmed before insertion
     */
    @Command("iprep allow add <ip>")
    @CommandDescription("Adds an IP to the IP reputation allowlist.")
    public void allowAdd(XCoreSender sender,
                         @Argument(value = "ip", description = "IP address to allowlist") String ip) {
        if (ip == null || ip.isBlank()) {
            Log.err("IP address cannot be blank.");
            return;
        }

        String normalized = ip.trim();
        if (allowlist.add(normalized)) {
            Log.info("Added @ to the IP reputation allowlist.", normalized);
        } else {
            Log.err("Failed to add @ to the allowlist.", normalized);
        }
    }

    /**
     * Removes the given IP address from the IP reputation allowlist.
     *
     * @param ip the IP address (or CIDR) to remove from the allowlist; leading and trailing whitespace will be ignored
     */
    @Command("iprep allow remove <ip>")
    @CommandDescription("Removes an IP from the IP reputation allowlist.")
    public void allowRemove(XCoreSender sender,
                            @Argument(value = "ip", description = "IP address to remove from allowlist") String ip) {
        if (ip == null || ip.isBlank()) {
            Log.err("IP address cannot be blank.");
            return;
        }

        String normalized = ip.trim();
        if (allowlist.remove(normalized)) {
            Log.info("Removed @ from the IP reputation allowlist.", normalized);
        } else {
            Log.err("Failed to remove @ from the allowlist.", normalized);
        }
    }

    /**
     * Prints all IPs currently stored in the IP reputation allowlist to the server console.
     *
     * If the allowlist is empty, logs a message indicating that; otherwise logs the total
     * number of entries followed by each entry on its own line.
     */
    @Command("iprep allow list")
    @CommandDescription("Lists all IPs on the IP reputation allowlist.")
    public void allowList(XCoreSender sender) {
        Set<String> entries = allowlist.list();
        if (entries.isEmpty()) {
            Log.info("IP reputation allowlist is empty.");
            return;
        }

        Log.info("IP reputation allowlist (@ entries):", entries.size());
        for (String entry : entries) {
            Log.info("  - @", entry);
        }
    }
}
