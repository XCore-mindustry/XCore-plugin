package org.xcore.plugin.ui.menu;

import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuPromptContext;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;

import static com.ospx.flubundle.Bundle.args;

final class DateTimePickerFlows {

    static final String ROUTE_PICKER = "shared.datetime-picker";

    private static final String ACTION_DATE_TODAY = "date:today";
    private static final String ACTION_DATE_TOMORROW = "date:tomorrow";
    private static final String ACTION_DATE_PLUS_TWO = "date:plus-two";
    private static final String ACTION_DATE_PLUS_SEVEN = "date:plus-seven";
    private static final String ACTION_TIME_NOW = "time:now";
    private static final String ACTION_TIME_0000 = "time:0000";
    private static final String ACTION_TIME_0600 = "time:0600";
    private static final String ACTION_TIME_1200 = "time:1200";
    private static final String ACTION_TIME_1800 = "time:1800";
    private static final String ACTION_MINUS_DAY = "adjust:-1d";
    private static final String ACTION_PLUS_DAY = "adjust:+1d";
    private static final String ACTION_MINUS_HOUR = "adjust:-1h";
    private static final String ACTION_PLUS_HOUR = "adjust:+1h";
    private static final String ACTION_MINUS_FIFTEEN = "adjust:-15m";
    private static final String ACTION_PLUS_FIFTEEN = "adjust:+15m";
    private static final String ACTION_APPLY = "apply";
    private static final String ACTION_RESET = "reset";
    private static final String ACTION_MANUAL = "manual";
    private static final String PROMPT_MANUAL = "shared.datetime-picker.manual";

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateTimePickerFlows() {
    }

    static PickerState state(String fieldLabelKey, long initialValue, LongConsumer applySelection) {
        PickerState state = new PickerState();
        state.fieldLabelKey = fieldLabelKey;
        state.selectedTime = initialValue;
        state.applySelection = applySelection;
        return state;
    }

    static final class PickerFlow extends BaseMenuFlow<PickerState> {

        PickerFlow() {
            super(ROUTE_PICKER, PickerState.class);

            action("back", ctx -> {
                clearState(ctx.session());
                ctx.goBack();
            });
            action("close", ctx -> {
                clearState(ctx.session());
                ctx.close();
            });

            action(ACTION_DATE_TODAY, ctx -> update(ctx, current -> setDate(current, 0)));
            action(ACTION_DATE_TOMORROW, ctx -> update(ctx, current -> setDate(current, 1)));
            action(ACTION_DATE_PLUS_TWO, ctx -> update(ctx, current -> setDate(current, 2)));
            action(ACTION_DATE_PLUS_SEVEN, ctx -> update(ctx, current -> setDate(current, 7)));
            action(ACTION_TIME_NOW, ctx -> update(ctx, current -> System.currentTimeMillis()));
            action(ACTION_TIME_0000, ctx -> update(ctx, current -> setTime(current, 0, 0)));
            action(ACTION_TIME_0600, ctx -> update(ctx, current -> setTime(current, 6, 0)));
            action(ACTION_TIME_1200, ctx -> update(ctx, current -> setTime(current, 12, 0)));
            action(ACTION_TIME_1800, ctx -> update(ctx, current -> setTime(current, 18, 0)));
            action(ACTION_MINUS_DAY, ctx -> update(ctx, current -> adjust(current, -Duration.ofDays(1).toMillis())));
            action(ACTION_PLUS_DAY, ctx -> update(ctx, current -> adjust(current, Duration.ofDays(1).toMillis())));
            action(ACTION_MINUS_HOUR, ctx -> update(ctx, current -> adjust(current, -Duration.ofHours(1).toMillis())));
            action(ACTION_PLUS_HOUR, ctx -> update(ctx, current -> adjust(current, Duration.ofHours(1).toMillis())));
            action(ACTION_MINUS_FIFTEEN, ctx -> update(ctx, current -> adjust(current, -Duration.ofMinutes(15).toMillis())));
            action(ACTION_PLUS_FIFTEEN, ctx -> update(ctx, current -> adjust(current, Duration.ofMinutes(15).toMillis())));
            action(ACTION_RESET, ctx -> {
                ctx.state().selectedTime = 0L;
                ctx.render();
            });
            action(ACTION_MANUAL, ctx -> ctx.openPrompt(new MenuPrompt(
                    PROMPT_MANUAL,
                    ctx.locale().t("date-time-picker-manual-title"),
                    ctx.locale().t("date-time-picker-manual-message"),
                    64,
                    ctx.state().selectedTime > 0 ? String.valueOf(ctx.state().selectedTime) : "",
                    false
            )));
            action(ACTION_APPLY, ctx -> {
                PickerState state = ctx.state();
                LongConsumer applySelection = state.applySelection;
                long selectedTime = state.selectedTime;

                clearState(ctx.session());
                if (applySelection != null) {
                    applySelection.accept(selectedTime);
                }

                if (!ctx.goBack()) {
                    ctx.close();
                }
            });

            onPrompt(PROMPT_MANUAL, this::handleManualSubmit, MenuRenderContext::render);
        }

        @Override
        public PickerState createState(Session session, MenuRoute route, PickerState currentState) {
            return currentState == null ? new PickerState() : currentState;
        }

        @Override
        public void onClose(MenuRenderContext<PickerState> context) {
            clearState(context.session());
        }

        @Override
        public MenuScreen render(MenuRenderContext<PickerState> context) {
            Session session = context.session();
            PickerState state = context.state();

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-today"), ACTION_DATE_TODAY),
                    MenuButton.of(session.locale().t("date-time-picker-tomorrow"), ACTION_DATE_TOMORROW),
                    MenuButton.of(session.locale().t("date-time-picker-plus-2d"), ACTION_DATE_PLUS_TWO)
            );
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-plus-7d"), ACTION_DATE_PLUS_SEVEN),
                    MenuButton.of(session.locale().t("date-time-picker-now"), ACTION_TIME_NOW)
            );
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-time-0000"), ACTION_TIME_0000),
                    MenuButton.of(session.locale().t("date-time-picker-time-0600"), ACTION_TIME_0600),
                    MenuButton.of(session.locale().t("date-time-picker-time-1200"), ACTION_TIME_1200)
            );
            grid.row(MenuButton.of(session.locale().t("date-time-picker-time-1800"), ACTION_TIME_1800));
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-minus-1d"), ACTION_MINUS_DAY),
                    MenuButton.of(session.locale().t("date-time-picker-plus-1d"), ACTION_PLUS_DAY)
            );
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-minus-1h"), ACTION_MINUS_HOUR),
                    MenuButton.of(session.locale().t("date-time-picker-plus-1h"), ACTION_PLUS_HOUR)
            );
            grid.row(
                    MenuButton.of(session.locale().t("date-time-picker-minus-15m"), ACTION_MINUS_FIFTEEN),
                    MenuButton.of(session.locale().t("date-time-picker-plus-15m"), ACTION_PLUS_FIFTEEN)
            );
            grid.row(
                    MenuButton.of("[green]" + session.locale().t("save"), ACTION_APPLY),
                    MenuButton.of("[scarlet]" + session.locale().t("date-time-picker-reset"), ACTION_RESET)
            );
            grid.row(MenuButton.of(session.locale().t("date-time-picker-manual"), ACTION_MANUAL));
            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("date-time-picker-title"),
                    session.locale().t("date-time-picker-content", args(
                            "field", resolveFieldLabel(session, state),
                            "value", formatTime(session, state.selectedTime)
                    )),
                    grid.build()
            );
        }

        private void handleManualSubmit(MenuPromptContext<PickerState> ctx) {
            ParseResult result = parseInput(ctx.text());
            if (!result.valid) {
                ctx.renderContext().session().locale().send(result.errorKey);
                ctx.renderContext().render();
                return;
            }

            ctx.renderContext().state().selectedTime = result.value;
            ctx.renderContext().render();
        }

        private void update(MenuRenderContext<PickerState> ctx, LongUnaryOperator operation) {
            ctx.state().selectedTime = operation.applyAsLong(ctx.state().selectedTime);
            ctx.render();
        }
    }

    static final class PickerState {
        public String fieldLabelKey = "date-time-picker-field-generic";
        public long selectedTime;
        public LongConsumer applySelection;
    }

    private static String resolveFieldLabel(Session session, PickerState state) {
        String key = state.fieldLabelKey == null || state.fieldLabelKey.isBlank()
                ? "date-time-picker-field-generic"
                : state.fieldLabelKey;
        return session.locale().t(key);
    }

    private static String formatTime(Session session, long millis) {
        if (millis <= 0) {
            return session.locale().t("never");
        }
        return FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZONE_ID));
    }

    private static void clearState(Session session) {
        session.clearDraft(PickerState.class);
    }

    private static long setDate(long currentValue, int daysFromToday) {
        ZonedDateTime base = Instant.ofEpochMilli(baseTime(currentValue)).atZone(ZONE_ID)
                .withSecond(0)
                .withNano(0);
        LocalDate targetDate = LocalDate.now(ZONE_ID).plusDays(daysFromToday);
        return targetDate.atTime(base.toLocalTime()).atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    private static long setTime(long currentValue, int hour, int minute) {
        ZonedDateTime base = Instant.ofEpochMilli(baseTime(currentValue)).atZone(ZONE_ID);
        return base.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static long adjust(long currentValue, long deltaMillis) {
        return Math.max(0L, baseTime(currentValue) + deltaMillis);
    }

    private static long baseTime(long currentValue) {
        return currentValue > 0 ? currentValue : System.currentTimeMillis();
    }

    private static ParseResult parseInput(String input) {
        if (input == null || input.isBlank()) {
            return ParseResult.valid(0L);
        }

        if (input.startsWith("+")) {
            if (input.length() < 3) {
                return ParseResult.invalid("error-wrong-period-format");
            }

            char unit = input.charAt(input.length() - 1);
            String valueStr = input.substring(1, input.length() - 1);
            long value;
            try {
                value = Long.parseLong(valueStr);
            } catch (NumberFormatException ignored) {
                return ParseResult.invalid("error-wrong-period-format");
            }

            long deltaMillis = switch (unit) {
                case 'm' -> Duration.ofMinutes(value).toMillis();
                case 'h' -> Duration.ofHours(value).toMillis();
                case 'd' -> Duration.ofDays(value).toMillis();
                default -> -1L;
            };
            if (deltaMillis < 0) {
                return ParseResult.invalid("error-wrong-period-format");
            }
            return ParseResult.valid(System.currentTimeMillis() + deltaMillis);
        }

        try {
            return ParseResult.valid(Long.parseLong(input));
        } catch (NumberFormatException ignored) {
            return ParseResult.invalid("error-wrong-number");
        }
    }

    private static final class ParseResult {
        private final boolean valid;
        private final long value;
        private final String errorKey;

        private ParseResult(boolean valid, long value, String errorKey) {
            this.valid = valid;
            this.value = value;
            this.errorKey = errorKey;
        }

        private static ParseResult valid(long value) {
            return new ParseResult(true, value, null);
        }

        private static ParseResult invalid(String errorKey) {
            return new ParseResult(false, 0L, errorKey);
        }
    }
}
