package com.personalrecords.api.exports;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class CsvWriter {

    private final BufferedWriter writer;

    CsvWriter(OutputStream output) throws IOException {
        output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }

    void writeRow(CsvField... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            String value = fields[i].value();
            if (fields[i].untrustedText()) {
                value = safeText(value);
            }
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    void flush() throws IOException {
        writer.flush();
    }

    static CsvField[] headers(String... labels) {
        return List.of(labels).stream().map(CsvWriter::raw).toArray(CsvField[]::new);
    }

    static CsvField text(String value) {
        return new CsvField(value == null ? "" : value, true);
    }

    static CsvField raw(Object value) {
        return new CsvField(value == null ? "" : value.toString(), false);
    }

    private String safeText(String value) {
        int index = 0;
        while (index < value.length()
                && (Character.isWhitespace(value.charAt(index)) || Character.isISOControl(value.charAt(index)))) {
            index++;
        }
        if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    record CsvField(String value, boolean untrustedText) {
    }
}
