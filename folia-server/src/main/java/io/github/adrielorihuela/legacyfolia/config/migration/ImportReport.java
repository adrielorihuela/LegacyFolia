package io.github.adrielorihuela.legacyfolia.config.migration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code import-report.txt} left in the backup: what was imported and where, which
 * collisions were resolved and how, and what had no place to go.
 *
 * <p>Plain ASCII, whatever the values hold, so that it reads the same in any terminal, editor or
 * paste site an operator opens it in. Anything outside ASCII is written as a {@code \}{@code uXXXX}
 * escape, the way Java properties files write it.</p>
 */
final class ImportReport {

    static final String FILE_NAME = "import-report.txt";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final List<String> files = new ArrayList<>();

    final List<String> strays = new ArrayList<>();

    final List<String> imported = new ArrayList<>();

    final List<String> replaced = new ArrayList<>();

    final List<String> kept = new ArrayList<>();

    final List<String> secrets = new ArrayList<>();

    int unchanged;

    String render(final LocalDateTime when, final String root, final ImportPlan plan, final List<String> notes) {
        final StringBuilder out = new StringBuilder();
        out.append("LegacyFolia import report\n");
        out.append("=========================\n\n");
        out.append("Date:        ").append(WHEN.format(when)).append('\n');
        out.append("Server root: ").append(root).append("\n\n");
        out.append("The files below were read, their settings written to the TOML files in config/,\n");
        out.append("and the originals moved into this folder with the same relative paths.\n");
        out.append("Only values that differ from LegacyFolia's defaults were written. With the\n");
        out.append("server.properties mode on, those values move to server.properties at start-up.\n");

        section(out, "Files imported", this.files);
        section(out, "Files backed up but not imported (the server was using the copy in config/)", this.strays);
        section(out, "Notes", notes);
        section(out, "Values imported (" + this.imported.size() + "; " + this.unchanged
            + " more already matched the TOML files)", this.imported);
        section(out, "TOML values replaced by the imported ones", this.replaced);
        section(out, "TOML values kept over the imported ones", this.kept);
        section(out, "Collisions between files, resolved as the original server did", plan.collisions);
        section(out, "Secrets moved into their own files (values not shown)", this.secrets);
        section(out, "NOT IMPORTED: no place in the TOML files (the values are still in the backed up files)", plan.unplaced);
        section(out, "NOT IMPORTED: values the setting cannot take (the setting keeps its default)", plan.invalid);
        section(out, "Ignored: file metadata, not settings", plan.ignored);
        return ascii(out.toString());
    }

    private static void section(final StringBuilder out, final String title, final List<String> lines) {
        out.append('\n').append(title).append('\n');
        out.append("-".repeat(Math.min(title.length(), 78))).append('\n');
        if (lines.isEmpty()) {
            out.append("  (none)\n");
            return;
        }
        for (final String line : lines) {
            out.append("  ").append(line.replace("\n", "\\n")).append('\n');
        }
    }

    /** Escapes everything outside printable ASCII, keeping line breaks. */
    static String ascii(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\n' || c >= 0x20 && c < 0x7f) {
                out.append(c);
            } else {
                out.append(String.format("\\u%04X", (int) c));
            }
        }
        return out.toString();
    }
}
