package org.xcore.plugin.integration.playerstorage;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerStoreSchema {
    public record Field(FieldType type, boolean indexed) { }
    private final Map<String, Field> fields;
    private final int version;
    private PlayerStoreSchema(Map<String, Field> fields, int version) {
        this.fields = Map.copyOf(fields); this.version = version;
    }
    public Map<String, Field> fields() { return fields; }
    public int version() { return version; }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private final Map<String, Field> fields = new LinkedHashMap<>(); private int version = 1;
        public Builder field(String name, FieldType type, boolean indexed) {
            if (name == null || !name.matches("[a-zA-Z][a-zA-Z0-9_]{0,31}") || name.startsWith("$") || name.contains(".")) throw new IllegalArgumentException("Invalid field name");
            if (type == null || fields.put(name, new Field(type, indexed)) != null) throw new IllegalArgumentException("Duplicate field: " + name);
            return this;
        }
        public Builder version(int version) { if (version < 1) throw new IllegalArgumentException("Schema version must be positive"); this.version = version; return this; }
        public PlayerStoreSchema build() { if (fields.isEmpty()) throw new IllegalArgumentException("At least one field is required"); return new PlayerStoreSchema(fields, version); }
    }
}
