package io.github.adrielorihuela.legacyfolia.bots;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scenario bookkeeping: results, assertions and the JSON report. */
final class Scn {
    record Result(String id, String scenario, String status, String detail) {
    }

    static final List<Result> RESULTS = new ArrayList<>();

    interface Body {
        /** Returns the detail of a PASS; throws {@link Fail} for FAIL and {@link NotRun} for NOT_RUN. */
        String run() throws Exception;
    }

    static final class Fail extends RuntimeException {
        Fail(String m) {
            super(m);
        }
    }

    static final class NotRun extends RuntimeException {
        NotRun(String m) {
            super(m);
        }
    }

    static boolean only(String group) {
        String o = System.getProperty("bench.only", "");
        return o.isEmpty() || List.of(o.split(",")).contains(group);
    }

    static void run(String id, String name, Body body) {
        String status;
        String detail;
        try {
            detail = body.run();
            status = "PASS";
        } catch (Fail f) {
            status = "FAIL";
            detail = f.getMessage();
        } catch (NotRun n) {
            status = "NOT_RUN";
            detail = n.getMessage();
        } catch (Exception e) {
            status = "FAIL";
            detail = "error in the bench: " + e;
        }
        record(id, name, status, detail);
    }

    static void record(String id, String name, String status, String detail) {
        synchronized (RESULTS) {
            RESULTS.add(new Result(id, name, status, detail));
        }
        System.out.printf("%-6s %-8s %s%n        %s%n", id, status, name, detail == null ? "" : detail.replace("\n", " | "));
    }

    static void notRun(String id, String name, String why) {
        record(id, name, "NOT_RUN", why);
    }

    static void need(boolean cond, String message) {
        if (!cond) {
            throw new Fail(message);
        }
    }

    /** Fails unless {@code actual} is within {@code tol} of {@code expected}; returns "label=actual" for the detail. */
    static String near(String label, double actual, double expected, double tol) {
        if (Math.abs(actual - expected) > tol) {
            throw new Fail(label + ": expected " + expected + " (+-" + tol + "), got " + actual);
        }
        return label + "=" + fmt(actual);
    }

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    static void write(Path out, String jar, String extra) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"generated\": ").append(q(Instant.now().toString()));
        sb.append(",\n  \"jar\": ").append(q(jar));
        sb.append(",\n  \"note\": ").append(q(extra));
        int pass = 0, fail = 0, nr = 0;
        for (Result r : RESULTS) {
            switch (r.status()) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                default -> nr++;
            }
        }
        sb.append(",\n  \"summary\": {\"PASS\": ").append(pass).append(", \"FAIL\": ").append(fail).append(", \"NOT_RUN\": ").append(nr).append("}");
        sb.append(",\n  \"results\": [\n");
        for (int i = 0; i < RESULTS.size(); i++) {
            Result r = RESULTS.get(i);
            sb.append("    {\"id\": ").append(q(r.id())).append(", \"scenario\": ").append(q(r.scenario())).append(", \"status\": ").append(q(r.status())).append(", \"detail\": ").append(q(r.detail())).append("}");
            sb.append(i + 1 < RESULTS.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
