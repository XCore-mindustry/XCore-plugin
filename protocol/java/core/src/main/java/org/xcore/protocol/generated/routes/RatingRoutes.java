package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.rating.RatingMessages;

public final class RatingRoutes {
    private RatingRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.RatingRoutes instances");
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

    public static final RouteDescriptor RATING_SEASON_STARTED_V1 = new RouteDescriptor(
            "rating",
            "ratingSeasonStartedV1Route",
            "rating.season.started",
            1,
            RatingMessages.RatingSeasonStartedV1.class,
            "event",
            "xcore:evt:rating:season-started",
            Map.of(),
            "broadcast",
            86400000,
            true,
            true,
            "rating-seasons",
            null
    );

    public static final RouteDescriptor RATING_SEASON_ENDING_SOON_V1 = new RouteDescriptor(
            "rating",
            "ratingSeasonEndingSoonV1Route",
            "rating.season.ending-soon",
            1,
            RatingMessages.RatingSeasonEndingSoonV1.class,
            "event",
            "xcore:evt:rating:season-ending-soon",
            Map.of(),
            "broadcast",
            86400000,
            true,
            true,
            "rating-seasons",
            null
    );

    public static final RouteDescriptor RATING_SEASON_ENDED_V1 = new RouteDescriptor(
            "rating",
            "ratingSeasonEndedV1Route",
            "rating.season.ended",
            1,
            RatingMessages.RatingSeasonEndedV1.class,
            "event",
            "xcore:evt:rating:season-ended",
            Map.of(),
            "broadcast",
            86400000,
            true,
            true,
            "rating-seasons",
            null
    );

    public static final RouteDescriptor RATING_SEASON_RESCHEDULED_V1 = new RouteDescriptor(
            "rating",
            "ratingSeasonRescheduledV1Route",
            "rating.season.rescheduled",
            1,
            RatingMessages.RatingSeasonRescheduledV1.class,
            "event",
            "xcore:evt:rating:season-rescheduled",
            Map.of(),
            "broadcast",
            86400000,
            true,
            true,
            "rating-seasons",
            null
    );

    public static final RouteDescriptor RATING_SEASON_RESCHEDULE_REQUEST_V1 = new RouteDescriptor(
            "rating",
            "ratingSeasonRescheduleRequestV1Route",
            "rating.season.reschedule.request",
            1,
            RatingMessages.RatingSeasonRescheduleRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "rating-seasons",
            new RouteResponseDescriptor(
                    "rating.season.reschedule.response",
                    1,
                    RatingMessages.RatingSeasonRescheduleResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final RouteDescriptor RATING_ACCOUNTS_MERGE_REQUEST_V1 = new RouteDescriptor(
            "rating",
            "ratingAccountsMergeRequestV1Route",
            "rating.accounts.merge.request",
            1,
            RatingMessages.RatingAccountsMergeRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "rating-accounts",
            new RouteResponseDescriptor(
                    "rating.accounts.merge.response",
                    1,
                    RatingMessages.RatingAccountsMergeResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("rating.season.started", 1), RATING_SEASON_STARTED_V1),
            entry(key("rating.season.ending-soon", 1), RATING_SEASON_ENDING_SOON_V1),
            entry(key("rating.season.ended", 1), RATING_SEASON_ENDED_V1),
            entry(key("rating.season.rescheduled", 1), RATING_SEASON_RESCHEDULED_V1),
            entry(key("rating.season.reschedule.request", 1), RATING_SEASON_RESCHEDULE_REQUEST_V1),
            entry(key("rating.accounts.merge.request", 1), RATING_ACCOUNTS_MERGE_REQUEST_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
