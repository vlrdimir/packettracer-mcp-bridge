package org.apache.commons.lang.builder;

public final class ToStringBuilder {
    private final StringBuilder builder;
    private boolean firstField = true;

    public ToStringBuilder(Object value) {
        String typeName = value == null ? "null" : value.getClass().getName();
        builder = new StringBuilder(typeName).append('[');
    }

    public ToStringBuilder append(String fieldName, Object value) {
        appendFieldPrefix(fieldName);
        builder.append(value);
        return this;
    }

    public ToStringBuilder append(String fieldName, int value) {
        appendFieldPrefix(fieldName);
        builder.append(value);
        return this;
    }

    @Override
    public String toString() {
        return builder.append(']').toString();
    }

    private void appendFieldPrefix(String fieldName) {
        if (!firstField) {
            builder.append(',');
        }

        firstField = false;
        builder.append(fieldName).append('=');
    }
}
