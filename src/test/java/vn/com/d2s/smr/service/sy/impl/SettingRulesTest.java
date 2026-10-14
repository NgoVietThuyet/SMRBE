package vn.com.d2s.smr.service.sy.impl;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingRulesTest {

    @Test
    void docxWithAllPlaceholdersSplitAcrossRunsIsAccepted() throws IOException {
        // Word thường tách "{{meeting_name}}" thành nhiều thẻ <w:t>.
        String xml = "<w:document><w:body><w:p><w:r><w:t>{{meeting_</w:t></w:r><w:r><w:t>name}}</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>{{time}} {{participants}} {{summary}} {{decisions}} {{action_items}}</w:t></w:r></w:p>"
                + "</w:body></w:document>";

        List<String> missing = DocxPlaceholderInspector.missingPlaceholders(new ByteArrayInputStream(docx(xml)));

        assertThat(missing).isEmpty();
    }

    @Test
    void docxMissingPlaceholdersIsReported() throws IOException {
        String xml = "<w:document><w:body><w:t>{{meeting_name}} {{time}}</w:t></w:body></w:document>";

        List<String> missing = DocxPlaceholderInspector.missingPlaceholders(new ByteArrayInputStream(docx(xml)));

        assertThat(missing).containsExactly("{{participants}}", "{{summary}}", "{{decisions}}", "{{action_items}}");
    }

    @Test
    void nonDocxIsRejected() {
        assertThatThrownBy(() -> DocxPlaceholderInspector.missingPlaceholders(
                new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extensionsAreNormalizedAndExecutablesBlocked() {
        assertThat(SettingServiceImpl.normalizeExtensions(List.of("PDF", ".docx", "pdf", " .Png ")))
                .containsExactly(".pdf", ".docx", ".png");

        assertThatThrownBy(() -> SettingServiceImpl.normalizeExtensions(List.of(".pdf", "exe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".exe");
        assertThatThrownBy(() -> SettingServiceImpl.normalizeExtensions(List.of(".p d f")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettingServiceImpl.normalizeExtensions(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] docx(String documentXml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
