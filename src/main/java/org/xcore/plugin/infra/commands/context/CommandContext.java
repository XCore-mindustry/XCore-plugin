package org.xcore.plugin.infra.commands.context;

public abstract class CommandContext {
    protected final String[] args;

    protected CommandContext(String[] args) {
        this.args = args;
    }

    public String[] args() {
        return args;
    }

    public String arg(int index) {
        return args.length > index ? args[index] : null;
    }

    public int argInt(int index, int defaultValue) {
        String val = arg(index);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long argLong(int index, long defaultValue) {
        String val = arg(index);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

//    public boolean argBool(int index, boolean defaultValue) {
//        String val = arg(index);
//        if (val == null) return defaultValue;
//        return Strings.canParseBoolean(val) ? Strings.parseBool(val) : defaultValue;
//    }

    public boolean isClientContext() {
        return this instanceof ClientContext;
    }


    public boolean isServerContext() {
        return this instanceof ServerContext;
    }
}