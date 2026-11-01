package org.xcore.plugin.integration.playerstorage;

import java.util.Objects;

public record SortField(String field, SortDirection direction) {
    public SortField {
        if (field == null || field.isBlank() || direction == null) throw new IllegalArgumentException("Invalid sort field");
    }
}
