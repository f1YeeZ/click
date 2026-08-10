package com.clicker.mousehub.util;

/** Escapes spreadsheet cells and neutralizes formula prefixes in untrusted text. */
public final class CsvSecurity {
    private CsvSecurity() {}

    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (value instanceof CharSequence && startsWithFormula(text)) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean startsWithFormula(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == ' ' || value == '\t' || value == '\r' || value == '\n') continue;
            return value == '=' || value == '+' || value == '-' || value == '@';
        }
        return false;
    }
}
