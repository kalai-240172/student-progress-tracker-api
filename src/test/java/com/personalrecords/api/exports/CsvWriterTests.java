package com.personalrecords.api.exports;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CsvWriterTests {

    @Test
    void writesUtf8BomAndEscapesCsvCharactersAndFormulaText() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CsvWriter writer = new CsvWriter(output);

        writer.writeRow(
                CsvWriter.text("  =SUM(1,2)"),
                CsvWriter.text("A \"quoted\", value\non two lines"),
                CsvWriter.raw(-35));
        writer.flush();

        String csv = output.toString(StandardCharsets.UTF_8);
        assertEquals("\uFEFF\"'  =SUM(1,2)\",\"A \"\"quoted\"\", value\non two lines\",\"-35\"\r\n", csv);
    }

    @Test
    void preservesFormulaLikeNumericValuesAndEscapesFormulaAfterControlCharacters() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CsvWriter writer = new CsvWriter(output);

        writer.writeRow(CsvWriter.raw(-12), CsvWriter.text("\t@SUM(A1:A2)"));
        writer.flush();

        assertEquals("\uFEFF\"-12\",\"'\t@SUM(A1:A2)\"\r\n", output.toString(StandardCharsets.UTF_8));
    }
}
