package com.virtualinvestor.core.codec;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.StringJoiner;

/**
 * Deterministic text codec for flat record types (commands and events). It is intentionally
 * dependency-free: the wire schema in the edge layer is a separate, versioned concern. The
 * format is one header line (record simple name) followed by one line per component
 * ({@code name<TAB>encodedValue}).
 */
public final class RecordTextCodec {

    private static final char NULL = '~';
    private static final char STRING = 's';
    private static final char ENUM = 'e';
    private static final char INTEGRAL = 'l';
    private static final char DECIMAL = 'd';
    private static final char BOOL = 'b';

    private RecordTextCodec() {
    }

    public static String encode(Object record) {
        if (record == null || !record.getClass().isRecord()) {
            throw new IllegalArgumentException("encode expects a record, got "
                    + (record == null ? "null" : record.getClass().getName()));
        }
        RecordComponent[] components = record.getClass().getRecordComponents();
        StringJoiner lines = new StringJoiner("\n");
        lines.add(record.getClass().getSimpleName());
        for (RecordComponent component : components) {
            Object value;
            try {
                value = component.getAccessor().invoke(record);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot read component " + component.getName(), e);
            }
            lines.add(component.getName() + "\t" + encodeValue(value));
        }
        return lines.toString();
    }

    public static <T> T decode(String text, Class<T> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("decode expects a record type, got " + type.getName());
        }
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !lines[0].equals(type.getSimpleName())) {
            throw new IllegalArgumentException("unexpected header for " + type.getSimpleName()
                    + ": " + (lines.length == 0 ? "<empty>" : lines[0]));
        }
        RecordComponent[] components = type.getRecordComponents();
        if (lines.length != components.length + 1) {
            throw new IllegalArgumentException("expected " + components.length + " components, got "
                    + (lines.length - 1));
        }
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String line = lines[i + 1];
            int tab = line.indexOf('\t');
            if (tab < 0) {
                throw new IllegalArgumentException("missing value separator on line " + (i + 1));
            }
            String name = line.substring(0, tab);
            if (!name.equals(components[i].getName())) {
                throw new IllegalArgumentException("component order mismatch: expected "
                        + components[i].getName() + ", got " + name);
            }
            parameterTypes[i] = components[i].getType();
            arguments[i] = decodeValue(parameterTypes[i], line.substring(tab + 1));
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot construct " + type.getName(), e);
        }
    }

    private static String encodeValue(Object value) {
        if (value == null) {
            return String.valueOf(NULL);
        }
        if (value instanceof String text) {
            return STRING + ":" + escape(text);
        }
        if (value instanceof Enum<?> enumeration) {
            return ENUM + ":" + enumeration.name();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return INTEGRAL + ":" + value;
        }
        if (value instanceof Double || value instanceof Float) {
            return DECIMAL + ":" + value;
        }
        if (value instanceof Boolean flag) {
            return BOOL + ":" + flag;
        }
        throw new IllegalArgumentException("unsupported component value: " + value.getClass().getName());
    }

    private static Object decodeValue(Class<?> type, String raw) {
        if (raw.length() == 1 && raw.charAt(0) == NULL) {
            return null;
        }
        char tag = raw.charAt(0);
        String body = raw.substring(2);
        return switch (tag) {
            case STRING -> unescape(body);
            case ENUM -> Enum.valueOf(type.asSubclass(Enum.class), body);
            case INTEGRAL -> integral(type, body);
            case DECIMAL -> Double.parseDouble(body);
            case BOOL -> Boolean.parseBoolean(body);
            default -> throw new IllegalArgumentException("unknown value tag '" + tag + "'");
        };
    }

    private static Object integral(Class<?> type, String body) {
        if (type == long.class || type == Long.class) {
            return Long.parseLong(body);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(body);
        }
        throw new IllegalArgumentException("unexpected integral type " + type.getName());
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case '\\' -> out.append('\\');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                default -> throw new IllegalArgumentException("bad escape \\" + next);
            }
        }
        return out.toString();
    }
}
