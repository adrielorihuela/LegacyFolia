package io.github.adrielorihuela.legacyfolia.bots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** A LegacyFolia server process in its own folder, driven through stdin and observed through its log. */
final class ServerProc implements AutoCloseable {
    final Path dir;
    final Process proc;
    final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final OutputStream stdin;

    private ServerProc(Path dir, Process proc) {
        this.dir = dir;
        this.proc = proc;
        this.stdin = proc.getOutputStream();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    lines.add(l);
                }
            } catch (IOException ignored) {
            }
        }, "server-log");
        t.setDaemon(true);
        t.start();
    }

    static ServerProc start(Path jar, Path dir, boolean keepStdin, String... args) throws IOException {
        Files.createDirectories(dir);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>(List.of(java, "-Xmx1536M", "-jar", jar.toAbsolutePath().toString(), "--nogui"));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        if (!keepStdin) {
            pb.redirectInput(ProcessBuilder.Redirect.from(Path.of(System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null").toFile()));
        }
        return new ServerProc(dir, pb.start());
    }

    /** Starts the server, waits for "Done (" and returns it, or throws with the tail of the log. */
    static ServerProc startReady(Path jar, Path dir, long timeoutMs, String... args) throws Exception {
        ServerProc s = start(jar, dir, true, args);
        if (s.await(Pattern.compile("Done \\("), 0, timeoutMs) == null) {
            String tail = s.tail(15);
            s.kill();
            throw new IllegalStateException("server did not reach Done within " + timeoutMs / 1000 + "s; log tail:\n" + tail);
        }
        return s;
    }

    String tail(int n) {
        synchronized (lines) {
            return String.join("\n", lines.subList(Math.max(0, lines.size() - n), lines.size()));
        }
    }

    int mark() {
        return lines.size();
    }

    String joined() {
        synchronized (lines) {
            return String.join("\n", lines);
        }
    }

    String joinedFrom(int from) {
        synchronized (lines) {
            return String.join("\n", lines.subList(Math.min(from, lines.size()), lines.size()));
        }
    }

    boolean alive() {
        return proc.isAlive();
    }

    /** First log line at or after {@code from} that contains a match, or null on timeout (or if the process ended). */
    String await(Pattern p, int from, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        int i = from;
        while (true) {
            synchronized (lines) {
                for (; i < lines.size(); i++) {
                    if (p.matcher(lines.get(i)).find()) {
                        return lines.get(i);
                    }
                }
            }
            if (System.currentTimeMillis() > end || (!proc.isAlive() && i >= lines.size())) {
                return null;
            }
            Thread.sleep(25);
        }
    }

    void send(String command) {
        try {
            stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            // the process is gone; the caller's await will time out
        }
    }

    /** Sends a command and returns the first log line matching {@code expect}, or null. */
    String cmd(String command, String expect, long ms) throws InterruptedException {
        int m = mark();
        send(command);
        return await(Pattern.compile(expect), m, ms);
    }

    /** Sends a command and returns everything logged during the next {@code waitMs}. */
    String cmdOut(String command, long waitMs) throws InterruptedException {
        int m = mark();
        send(command);
        Thread.sleep(waitMs);
        return joinedFrom(m);
    }

    /** Runs an {@code execute if ...} condition and says whether it passed. Null if the server gave no verdict. */
    Boolean test(String condition) throws InterruptedException {
        int m = mark();
        send("execute " + condition);
        String l = await(Pattern.compile("Test (passed|failed)"), m, 5000);
        if (l == null) {
            return null;
        }
        return l.contains("Test passed");
    }

    /** Stops the server cleanly and returns true if it exited by itself. */
    boolean stop() {
        send("stop");
        try {
            if (proc.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                return true;
            }
        } catch (InterruptedException ignored) {
        }
        kill();
        return false;
    }

    void kill() {
        proc.descendants().forEach(ProcessHandle::destroyForcibly);
        proc.destroyForcibly();
    }

    @Override
    public void close() {
        if (proc.isAlive()) {
            stop();
        }
    }
}
