package org.xcore.ui.responsive;

import arc.Core;
import arc.mock.MockApplication;
import arc.mock.MockGL20;
import arc.mock.MockGraphics;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WrapTable;
import arc.util.Align;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the primitives the responsive layer relies on, by running real Arc layout
 * instead of asserting on generated DSL.
 *
 * <p>The design rests on one claim: {@code growX() + maxWidth(n)} resolves on the client
 * to {@code min(available, n)}, so a server can hand the same tree to every player and let
 * each device size itself. If that were false the layer would have to fall back to
 * condition-gated variants, so it needs a real test rather than a comment.
 */
class LayoutPrimitiveTest {

    private MockApplication app;
    private MockGraphics graphics;

    @BeforeEach
    void setUp() {
        app = new MockApplication();
        graphics = new MockGraphics();
        Core.app = app;
        // Core.app is a plain static field, so the sub-systems have to be wired by hand
        Core.graphics = graphics;
        // Scene's ScreenViewport calls glViewport during construction
        Core.gl = new MockGL20();
    }

    @AfterEach
    void tearDown() {
        Core.scene = null;
        Core.graphics = null;
        Core.gl = null;
        Core.app = null;
    }

    /** Builds a fixed-size parent, configures the cell holding the child, forces layout. */
    private static Table layoutChildIn(float parentWidth, float parentHeight, CellConfig config) {
        Core.scene = new Scene();

        Table parent = new Table();
        parent.setSize(parentWidth, parentHeight);
        parent.setFillParent(false);

        Table child = new Table();
        config.apply(parent.add(child));

        parent.invalidateHierarchy();
        parent.validate();
        return child;
    }

    @FunctionalInterface
    private interface CellConfig {
        void apply(Cell<Table> cell);
    }

    @Test
    @DisplayName("growX + maxWidth clamps to the available width when the container is narrow")
    void growXMaxWidthClampsToAvailableWidth() {
        // 430 px available (typical phone portrait), capped at 900
        Table child = layoutChildIn(430f, 600f, cell -> cell.growX().maxWidth(900f));

        assertThat(child.getWidth()).isEqualTo(430f);
    }

    @Test
    @DisplayName("growX + maxWidth caps at maxWidth when the container is wide")
    void growXMaxWidthCapsAtMaxWidth() {
        // 1600 px available (desktop), capped at 900
        Table child = layoutChildIn(1600f, 900f, cell -> cell.growX().maxWidth(900f));

        assertThat(child.getWidth()).isEqualTo(900f);
    }

    @Test
    @DisplayName("growX + maxWidth adapts across the whole device range with no condition")
    void growXMaxWidthIsAdaptiveAcrossDeviceRange() {
        record Device(String name, float available, float expected) {
        }
        Device[] devices = {
                new Device("small phone portrait", 360f, 360f),
                new Device("phone portrait", 430f, 430f),
                new Device("phone landscape", 780f, 780f),
                new Device("tablet", 1100f, 900f),
                new Device("desktop", 1920f, 900f),
        };

        for (Device device : devices) {
            Table child = layoutChildIn(device.available(), 800f, cell -> cell.growX().maxWidth(900f));
            assertThat(child.getWidth())
                    .as("device: %s", device.name())
                    .isEqualTo(device.expected());
        }
    }

    /** Dialog shell: fixed header, growing scroll body, fixed footer. */
    private static Table dialog(float width, float height) {
        Core.scene = new Scene();

        Table root = new Table();
        root.setSize(width, height);
        root.setFillParent(false);

        root.add(new Table()).growX().height(80f).row();          // header
        root.add(new ScrollPane(new Table(), new ScrollPane.ScrollPaneStyle()))
            .grow().maxHeight(420f).row();                         // body
        root.add(new Table()).growX().height(80f).row();          // footer

        root.invalidateHierarchy();
        root.validate();
        return root;
    }

    @Test
    @DisplayName("growY + maxHeight bounds the body so the footer stays inside a tall dialog")
    void bodyIsBoundedOnTallDialog() {
        Table root = dialog(600f, 1000f);
        Table footer = (Table) root.getChildren().get(2);

        // header 80 + footer 80 = 160, so 840 is left over but the body is capped at 420
        assertThat(footer.getHeight()).isEqualTo(80f);
        assertThat(footer.getY(Align.bottom)).isEqualTo(0f);
    }

    @Test
    @DisplayName("growY + maxHeight squeezes the body instead of pushing the footer off screen")
    void footerStaysReachableOnShortDialog() {
        // landscape phone: 300px tall, so header(80) + footer(80) leave only 140 for the body
        Table root = dialog(600f, 300f);
        Table footer = (Table) root.getChildren().get(2);

        assertThat(footer.getHeight()).isEqualTo(80f);
        assertThat(footer.getY(Align.bottom)).isEqualTo(0f);
        assertThat(footer.getY(Align.top)).isEqualTo(80f);
    }

    @Test
    @DisplayName("a WrapTable picks its column count from the width it is actually given")
    void wrapTableAdaptsColumnCountToAvailableWidth() {
        record Case(String name, float available, int expectedColumns) {
        }
        Case[] cases = {
                new Case("phone portrait", 400f, 2),
                new Case("phone landscape", 800f, 4),
                new Case("desktop", 1200f, 6),
        };
        float cardWidth = 180f;

        for (Case testCase : cases) {
            Core.scene = new Scene();

            arc.scene.ui.layout.WrapTable cards = new WrapTable();
            cards.setSize(testCase.available(), 600f);
            cards.setFillParent(false);
            for (int i = 0; i < 12; i++) {
                cards.add(new Table()).width(cardWidth).height(50f);
            }

            cards.invalidateHierarchy();
            cards.validate();

            // group children by row: the widest row is the column count WrapTable chose
            Map<Float, Integer> perRow = new LinkedHashMap<>();
            for (Element child : cards.getChildren()) {
                perRow.merge(child.getY(Align.bottom), 1, Integer::sum);
            }
            int columns = perRow.values().stream().max(Integer::compareTo).orElse(0);

            assertThat(columns)
                    .as("columns on %s", testCase.name())
                    .isEqualTo(testCase.expectedColumns);
        }
    }
}