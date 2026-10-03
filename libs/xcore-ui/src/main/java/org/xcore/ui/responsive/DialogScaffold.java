package org.xcore.ui.responsive;

import org.xcore.ui.Ui;
import org.xcore.ui.VNode;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Layout shell for server-driven dialogs: sticky header, bounded scrolling body, sticky footer.
 *
 * <p>Every width here is {@code growX() + maxWidth(cap)}, so the dialog fills whatever the client
 * actually has and stops growing at the cap. The body is {@code growY() + maxHeight(cap)}, so it
 * takes the space left over after the header and footer and is squeezed rather than pushing them
 * off screen on a short screen. Nothing in this class depends on the server knowing the device.
 *
 * <p>For layout that cannot be expressed relatively, use {@link Responsive#portrait} /
 * {@link Responsive#landscape} inside the header or body callbacks.
 */
public final class DialogScaffold {

    private final DialogMetrics metrics;
    private Consumer<Ui.TableBuilder> header;
    private Consumer<Ui.TableBuilder> tabs;
    private Consumer<Ui.TableBuilder> body;
    private Consumer<Ui.TableBuilder> footer;
    private String background = "pane";
    private String dividerColor = "3b4252";
    private float dividerHeight = 2f;
    private boolean bodyScrollable = true;

    private DialogScaffold(DialogMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public static DialogScaffold create(DialogMetrics metrics) {
        return new DialogScaffold(metrics);
    }

    public DialogMetrics metrics() {
        return metrics;
    }

    public DialogScaffold background(String background) {
        this.background = background;
        return this;
    }

    public DialogScaffold dividerColor(String color) {
        this.dividerColor = color;
        return this;
    }

    public DialogScaffold scrollable(boolean scrollable) {
        this.bodyScrollable = scrollable;
        return this;
    }

    public DialogScaffold header(Consumer<Ui.TableBuilder> header) {
        this.header = header;
        return this;
    }

    public DialogScaffold tabs(Consumer<Ui.TableBuilder> tabs) {
        this.tabs = tabs;
        return this;
    }

    public DialogScaffold body(Consumer<Ui.TableBuilder> body) {
        this.body = body;
        return this;
    }

    public DialogScaffold footer(Consumer<Ui.TableBuilder> footer) {
        this.footer = footer;
        return this;
    }

    /**
     * A row that is a single full-width tap target, bounded by the card cap so long content wraps
     * instead of widening the scroll pane.
     */
    public Ui.TableBuilder card(Ui.TableBuilder parent, String actionId, Consumer<Ui.TableBuilder> content) {
        return parent.buttonTable(actionId, card -> {
            card.style("default");
            card.layout(l -> l.growX().maxWidth(metrics.maxCardWidth()).padBottom(4f));

            card.table(inner -> {
                inner.layout(l -> l.growX());
                content.accept(inner);
            });
        });
    }

    /** Horizontal rule spanning the content width. */
    public void divider(Ui.TableBuilder table) {
        table.image("whiteui", l -> l
                .growX()
                .maxWidth(metrics.maxDialogWidth())
                .height(dividerHeight)
                .padTop(4f)
                .padBottom(6f)
                .color(dividerColor)
        ).row();
    }

    public VNode build() {
        return Ui.table(root -> {
            if (background != null) {
                root.background(background);
            }
            root.layout(l -> l.growX().maxWidth(metrics.maxDialogWidth()).pad(4f));

            if (header != null) {
                root.add(Ui.table(h -> {
                    h.layout(l -> l.growX());
                    header.accept(h);
                })).row();
                divider(root);
            }

            if (tabs != null) {
                root.add(Ui.table(t -> {
                    // a WrapTable picks its tab columns from the width the client gives it
                    t.wrap();
                    t.layout(l -> l.growX());
                    tabs.accept(t);
                })).row();
                divider(root);
            }

            if (body != null) {
                if (bodyScrollable) {
                    root.pane(pane -> {
                        pane.layout(l -> l.growX().growY().maxHeight(metrics.maxBodyHeight()));
                        pane.table(cards -> {
                            cards.layout(l -> l.growX());
                            body.accept(cards);
                        });
                    }).row();
                } else {
                    root.add(Ui.table(b -> {
                        b.layout(l -> l.growX());
                        body.accept(b);
                    })).row();
                }

                if (footer != null) {
                    divider(root);
                }
            }

            if (footer != null) {
                root.add(Ui.table(f -> {
                    f.layout(l -> l.growX());
                    footer.accept(f);
                })).row();
            }
        });
    }
}