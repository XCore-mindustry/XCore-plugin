package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.cases.CasesMessages;

public final class CasesRoutes {
    private CasesRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.CasesRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream,
            Map<String, String> bindings
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            Map<String, String> bindings,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor CASES_REPORT_SUBMITTED_V1 = new RouteDescriptor(
            "cases",
            "casesReportSubmittedV1Route",
            "cases.report.submitted",
            1,
            CasesMessages.CasesReportSubmittedV1.class,
            "event",
            "xcore:evt:cases:report",
            Map.of(),
            "broadcast",
            604800000,
            true,
            true,
            "cases",
            null
    );

    public static final RouteDescriptor CASES_NOTICE_COMMAND_V1 = new RouteDescriptor(
            "cases",
            "casesNoticeCommandV1Route",
            "cases.notice.command",
            1,
            CasesMessages.CasesNoticeCommandV1.class,
            "command",
            "xcore:cmd:cases-notice:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "cases",
            null
    );

    public static final RouteDescriptor CASES_EVIDENCE_REQUEST_V1 = new RouteDescriptor(
            "cases",
            "casesEvidenceRequestV1Route",
            "cases.evidence.request",
            1,
            CasesMessages.CasesEvidenceRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            15000,
            false,
            false,
            "cases",
            new RouteResponseDescriptor(
                    "cases.evidence.response",
                    1,
                    CasesMessages.CasesEvidenceResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("cases.report.submitted", 1), CASES_REPORT_SUBMITTED_V1),
            entry(key("cases.notice.command", 1), CASES_NOTICE_COMMAND_V1),
            entry(key("cases.evidence.request", 1), CASES_EVIDENCE_REQUEST_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
