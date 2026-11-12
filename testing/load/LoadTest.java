// LegacyFolia load test. Run it through run-load.ps1 or run-load.sh, which download SoulFire first;
// or directly: java testing/load/LoadTest.java --host H --port P --soulfire-jar JAR --rcon-password-file F
//
// It starts one SoulFire process with --max offline bots joining one every --join-delay-ms, teleports
// each bot to one of --areas far-apart areas so that Folia puts them in different regions, and at every
// multiple of --step bots it measures the server over RCON. It stops at the first limit it hits.

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

public class LoadTest {
    // ---------------------------------------------------------------- options
    static final Map<String, String> OPT = new HashMap<>();

    static String opt(String key, String def) {
        return OPT.getOrDefault(key, def);
    }

    static int optInt(String key, int def) {
        return Integer.parseInt(opt(key, String.valueOf(def)).trim());
    }

    // ---------------------------------------------------------------- RCON
    static final class Rcon implements Closeable {
        final String host;
        final int port;
        final String password;
        Socket socket;
        DataInputStream in;
        OutputStream out;
        int nextId = 10;

        Rcon(String host, int port, String password) throws IOException {
            this.host = host;
            this.port = port;
            this.password = password;
            connect();
        }

        void connect() throws IOException {
            close();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = socket.getOutputStream();
            socket.setSoTimeout(15000);
            write(1, 3, password);
            int[] head = new int[2];
            String ignored = read(head);
            if (head[0] == -1) {
                throw new IOException("RCON rejected the password");
            }
        }

        void write(int id, int type, String body) throws IOException {
            byte[] b = body.getBytes(StandardCharsets.ISO_8859_1);
            ByteBuffer bb = ByteBuffer.allocate(14 + b.length).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(10 + b.length).putInt(id).putInt(type).put(b).put((byte) 0).put((byte) 0);
            out.write(bb.array());
            out.flush();
        }

        String read(int[] head) throws IOException {
            byte[] lenBytes = in.readNBytes(4);
            if (lenBytes.length < 4) {
                throw new EOFException("RCON closed the connection");
            }
            int len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] d = in.readNBytes(len);
            if (d.length < len || len < 10) {
                throw new EOFException("short RCON packet");
            }
            ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
            head[0] = bb.getInt();
            head[1] = bb.getInt();
            return new String(d, 8, len - 10, StandardCharsets.ISO_8859_1);
        }

        /** Sends a command and returns the whole response, colour codes stripped. */
        synchronized String cmd(String command) throws IOException {
            try {
                return cmd0(command);
            } catch (IOException e) {
                connect();
                return cmd0(command);
            }
        }

        private String cmd0(String command) throws IOException {
            int id = nextId++;
            write(id, 2, command);
            StringBuilder sb = new StringBuilder();
            int[] head = new int[2];
            socket.setSoTimeout(30000);
            sb.append(read(head));
            socket.setSoTimeout(150); // a long answer arrives as several packets back to back
            try {
                while (true) {
                    sb.append(read(head));
                }
            } catch (SocketTimeoutException done) {
                // end of the answer
            } finally {
                socket.setSoTimeout(15000);
            }
            return sb.toString().replaceAll("§.", "");
        }

        @Override
        public void close() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    // ---------------------------------------------------------------- parsing /tps
    record Region(String where, double util, double mspt, double tps, int chunks, int players) {}

    record Tps(int players, int regions, double util, double low, double median, double high, List<Region> top) {
        double worstMspt() {
            return top.stream().mapToDouble(Region::mspt).max().orElse(Double.NaN);
        }

        Region worst() {
            return top.stream().max(Comparator.comparingDouble(Region::mspt)).orElse(null);
        }
    }

    static double num(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    static Tps parseTps(String text) {
        double regions = num(text, "Total regions:\\s*(\\d+)");
        double low = num(text, "Lowest Region TPS:\\s*([\\d.]+)");
        if (Double.isNaN(regions) || Double.isNaN(low)) {
            return null;
        }
        List<Region> top = new ArrayList<>();
        Matcher m = Pattern.compile("Region around block \\[([^\\]]*)\\]:\\s*([\\d.]+)% util at ([\\d.]+) MSPT at ([\\d.]+) TPS\\s*"
            + "Chunks:\\s*(\\d+)\\s*Players:\\s*(\\d+)").matcher(text);
        while (m.find()) {
            top.add(new Region(m.group(1), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)),
                Double.parseDouble(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6))));
        }
        return new Tps((int) num(text, "Online Players:\\s*(\\d+)"), (int) regions, num(text, "Utilisation:\\s*([\\d.]+)%"),
            low, num(text, "Median Region TPS:\\s*([\\d.]+)"), num(text, "Highest Region TPS:\\s*([\\d.]+)"), top);
    }

    // ---------------------------------------------------------------- memory of the server
    /** Heap and GC figures; NaN when unknown. */
    record Mem(double heapUsedMb, double heapMaxMb, double gcCount, double gcTimeMs, double rssMb, String source) {}

    static final Mem NO_MEM = new Mem(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "none");

    static boolean sparkAvailable;
    static long serverPid = -1;
    static double serverXmxMb = Double.NaN;

    static boolean windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static String run(int seconds, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    static String jdkTool(String name) {
        Path p = Path.of(System.getProperty("java.home"), "bin", windows() ? name + ".exe" : name);
        return Files.exists(p) ? p.toString() : name;
    }

    static long detectPid(int port) {
        String out = windows()
            ? run(15, "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-NetTCPConnection -LocalPort " + port + " -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess")
            : run(10, "lsof", "-t", "-nP", "-iTCP:" + port, "-sTCP:LISTEN");
        Matcher m = Pattern.compile("\\d+").matcher(out);
        return m.find() ? Long.parseLong(m.group()) : -1;
    }

    static double maxHeapMb(long pid) {
        Matcher m = Pattern.compile("MaxHeapSize\\s*=\\s*(\\d+)").matcher(run(15, jdkTool("jcmd"), String.valueOf(pid), "VM.flags"));
        return m.find() ? Long.parseLong(m.group(1)) / 1048576.0 : Double.NaN;
    }

    static double rssMb(long pid) {
        String s = windows()
            ? run(15, "powershell", "-NoProfile", "-NonInteractive", "-Command", "(Get-Process -Id " + pid + ").WorkingSet64")
            : run(10, "ps", "-o", "rss=", "-p", String.valueOf(pid));
        Matcher m = Pattern.compile("\\d+").matcher(s);
        if (!m.find()) {
            return Double.NaN;
        }
        double v = Double.parseDouble(m.group());
        return windows() ? v / 1048576.0 : v / 1024.0;
    }

    static double toMb(double v, String unit) {
        return switch (unit.toUpperCase()) {
            case "KB" -> v / 1024;
            case "GB" -> v * 1024;
            case "TB" -> v * 1048576;
            default -> v;
        };
    }

    static Mem sparkMem(Rcon rcon) throws IOException {
        String t = rcon.cmd("spark health");
        int at = t.indexOf("Memory");
        Matcher m = Pattern.compile("([\\d.]+)\\s*(KB|MB|GB|TB)\\s*/\\s*([\\d.]+)\\s*(KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE)
            .matcher(at >= 0 ? t.substring(at) : t);
        if (!m.find()) {
            return null;
        }
        Matcher gc = Pattern.compile("(?m)^.*(G1|Young|Old|ZGC|Shenandoah|collector).*$").matcher(t);
        return new Mem(toMb(Double.parseDouble(m.group(1)), m.group(2)), toMb(Double.parseDouble(m.group(3)), m.group(4)),
            Double.NaN, Double.NaN, Double.NaN, "spark");
    }

    static Mem localMem() {
        if (serverPid < 0) {
            return NO_MEM;
        }
        double used = Double.NaN;
        double capacity = Double.NaN;
        double gcCount = Double.NaN;
        double gcMs = Double.NaN;
        String[] lines = run(15, jdkTool("jstat"), "-gc", String.valueOf(serverPid)).trim().split("\\R");
        if (lines.length >= 2) {
            String[] h = lines[0].trim().split("\\s+");
            String[] v = lines[lines.length - 1].trim().split("\\s+");
            Map<String, Double> col = new HashMap<>();
            for (int i = 0; i < h.length && i < v.length; i++) {
                try {
                    col.put(h[i], Double.parseDouble(v[i]));
                } catch (NumberFormatException ignored) {
                    // not a number
                }
            }
            if (col.containsKey("EU") && col.containsKey("OU")) {
                used = (col.getOrDefault("S0U", 0.0) + col.getOrDefault("S1U", 0.0) + col.get("EU") + col.get("OU")) / 1024.0;
                capacity = (col.getOrDefault("S0C", 0.0) + col.getOrDefault("S1C", 0.0) + col.getOrDefault("EC", 0.0)
                    + col.getOrDefault("OC", 0.0)) / 1024.0;
                gcCount = col.getOrDefault("YGC", 0.0) + col.getOrDefault("FGC", 0.0) + col.getOrDefault("CGC", 0.0);
                gcMs = col.getOrDefault("GCT", Double.NaN) * 1000.0;
            }
        }
        double max = Double.isNaN(serverXmxMb) ? capacity : serverXmxMb;
        return new Mem(used, max, gcCount, gcMs, rssMb(serverPid), "jstat");
    }

    // ---------------------------------------------------------------- bots
    static final Pattern LIST = Pattern.compile("There are (\\d+) of a max of (\\d+) players online:(.*)", Pattern.DOTALL);

    static Process soulfire;
    static Path soulfireLog;

    static Process startBots(String jar, Path accounts, String host, int port, int delay, String ram) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java").toString(),
            "-Dsf.vulkan.software=true", "-Xmx" + ram, "-jar", jar, "--start", "--account-file", accounts.toString(),
            "--account-type", "OFFLINE", "--bot-address", host + ":" + port, "--bot-join-delay-min", String.valueOf(delay),
            "--bot-join-delay-max", String.valueOf(delay), "--bot-concurrent-connects", opt("concurrent", "2"),
            "--auto-respawn-enabled", "--anti-afk-enabled"));
        if (!opt("protocol", "").isEmpty()) {
            cmd.add("--bot-protocol-version");
            cmd.add(opt("protocol", ""));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(soulfireLog.toFile());
        // Software rendering: the bots draw nothing, and a GPU driver that crashes at start-up takes SoulFire with it.
        pb.environment().put("VK_LOADER_DRIVERS_DISABLE", "*");
        return pb.start();
    }

    static void stopBots() {
        if (soulfire != null) {
            soulfire.descendants().forEach(ProcessHandle::destroyForcibly);
            soulfire.destroyForcibly();
        }
    }

    // ---------------------------------------------------------------- run state
    static Rcon rcon;
    static final Set<String> everJoined = new TreeSet<>();
    static final Set<String> online = new TreeSet<>();
    static final Set<String> placed = new HashSet<>();
    static final Map<String, Integer> placeTries = new HashMap<>();
    static int maxPlayers = Integer.MAX_VALUE;
    static long lastGrowth = System.currentTimeMillis();
    static String prefix;
    static int areas;
    static int spacing;
    static final Random RNG = new Random(7);

    /** One round: who is online, place the new ones. */
    static void poll() throws IOException {
        Matcher m = LIST.matcher(rcon.cmd("list"));
        if (!m.find()) {
            return;
        }
        maxPlayers = Integer.parseInt(m.group(2));
        Set<String> now = new TreeSet<>();
        for (String n : m.group(3).split(",")) {
            n = n.trim();
            if (n.startsWith(prefix)) {
                now.add(n);
            }
        }
        if (!everJoined.containsAll(now)) {
            lastGrowth = System.currentTimeMillis();
        }
        everJoined.addAll(now);
        online.clear();
        online.addAll(now);
        int budget = 40;
        for (String n : now) {
            if (budget == 0) {
                break;
            }
            if (!placed.contains(n) && placeTries.merge(n, 1, Integer::sum) <= 6) {
                budget--;
                place(n);
            }
        }
    }

    static void place(String name) throws IOException {
        int idx = Integer.parseInt(name.substring(prefix.length())) - 1;
        int area = idx % areas;
        int cols = (int) Math.ceil(Math.sqrt(areas));
        int x = (area % cols - cols / 2) * spacing + RNG.nextInt(97) - 48;
        int z = (area / cols - cols / 2) * spacing + RNG.nextInt(97) - 48;
        // Slow falling so that the drop from the top of the world is harmless wherever the ground turns out to be.
        rcon.cmd("effect give " + name + " minecraft:slow_falling infinite 0 true");
        if (rcon.cmd("tp " + name + " " + x + " 319 " + z).contains("Teleported")) {
            placed.add(name);
        }
    }

    // ---------------------------------------------------------------- measurements
    record Sample(Tps tps, Mem mem, double hostCpu, int online, int kicked) {}

    static final com.sun.management.OperatingSystemMXBean OS =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    static Sample sample() throws IOException {
        poll();
        Tps tps = parseTps(rcon.cmd("tps"));
        Mem mem = NO_MEM;
        if (sparkAvailable) {
            Mem s = sparkMem(rcon);
            mem = s != null ? s : NO_MEM;
        }
        if (Double.isNaN(mem.heapUsedMb())) {
            mem = localMem();
        }
        double cpu = OS.getCpuLoad();
        return new Sample(tps, mem, cpu < 0 ? Double.NaN : cpu * 100, online.size(), everJoined.size() - online.size());
    }

    static String fmt(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.2f", v);
    }

    static String violations(Sample s, double lastGc) {
        List<String> v = new ArrayList<>();
        if (s.tps() == null) {
            return "tps unreadable";
        }
        if (s.tps().worstMspt() > 50) {
            v.add("region MSPT " + fmt(s.tps().worstMspt()) + " > 50");
        }
        if (s.tps().low() < 18) {
            v.add("TPS " + fmt(s.tps().low()) + " < 18");
        }
        return String.join("; ", v);
    }

    static double heapPct(Sample s) {
        return s.mem().heapUsedMb() / s.mem().heapMaxMb() * 100;
    }

    // ---------------------------------------------------------------- main
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument " + a);
            }
            String key = a.substring(2);
            if (key.equals("check") || key.equals("help")) {
                OPT.put(key, "true");
            } else {
                OPT.put(key, args[++i]);
            }
        }
        if (OPT.containsKey("help")) {
            System.out.println("See docs/LOAD-TESTING.md. Options: --host --port --rcon-port --rcon-password-file --step --max --join-delay-ms "
                + "--bot-ram --soulfire-jar --areas --area-spacing --hold-seconds --samples --settle-seconds --server-pid --server-xmx-mb --check");
            return;
        }
        String host = opt("host", "127.0.0.1");
        int port = optInt("port", 25565);
        int rconPort = optInt("rcon-port", 25575);
        int step = optInt("step", 50);
        int max = optInt("max", 500);
        int delay = optInt("join-delay-ms", 1000);
        int hold = optInt("hold-seconds", 30);
        int samples = optInt("samples", 3);
        int settle = optInt("settle-seconds", 15);
        int stall = optInt("stall-seconds", 120);
        int tolerance = optInt("kick-tolerance", 0);
        prefix = opt("prefix", "lfb");
        areas = optInt("areas", 16);
        spacing = optInt("area-spacing", 1000);

        String password = System.getenv("LOAD_RCON_PASSWORD");
        if (OPT.containsKey("rcon-password-file")) {
            password = Files.readString(Path.of(opt("rcon-password-file", ""))).trim();
        }
        if (OPT.containsKey("rcon-password")) {
            password = opt("rcon-password", "");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("no RCON password: use --rcon-password-file, --rcon-password or LOAD_RCON_PASSWORD");
        }

        // ---- preflight
        try {
            rcon = new Rcon(host, rconPort, password);
        } catch (IOException e) {
            System.err.println("Cannot use RCON at " + host + ":" + rconPort + ": " + e.getMessage()
                + "\nEnable it on the server (network.rcon in config/network.toml) and give the password; see docs/LOAD-TESTING.md.");
            System.exit(2);
        }
        Tps first = parseTps(rcon.cmd("tps"));
        if (first == null) {
            System.err.println("The server did not answer /tps in the Folia format. This tool needs Folia or LegacyFolia.");
            System.exit(2);
        }
        Matcher lm = LIST.matcher(rcon.cmd("list"));
        if (lm.find()) {
            maxPlayers = Integer.parseInt(lm.group(2));
        }
        String spark = rcon.cmd("spark health");
        sparkAvailable = !spark.contains("Unknown or incomplete") && !spark.isBlank();
        boolean local = Set.of("127.0.0.1", "localhost", "::1").contains(host.toLowerCase());
        serverPid = OPT.containsKey("server-pid") ? Long.parseLong(opt("server-pid", "-1")) : (local ? detectPid(port) : -1);
        if (serverPid > 0) {
            serverXmxMb = OPT.containsKey("server-xmx-mb") ? Double.parseDouble(opt("server-xmx-mb", "")) : maxHeapMb(serverPid);
        } else if (OPT.containsKey("server-xmx-mb")) {
            serverXmxMb = Double.parseDouble(opt("server-xmx-mb", ""));
        }
        System.out.printf("Server %s:%d (RCON %d): %d players of max %d, %d regions, spark %s, local pid %s, Xmx %s MB%n", host, port, rconPort,
            first.players(), maxPlayers, first.regions(), sparkAvailable ? "yes" : "no (use a local pid or install the spark plugin for heap figures)",
            serverPid > 0 ? serverPid : "none", Double.isNaN(serverXmxMb) ? "unknown" : fmt(serverXmxMb));
        if (OPT.containsKey("check")) {
            System.out.println("Check only: RCON works and /tps is readable.");
            return;
        }
        if (!sparkAvailable && serverPid < 0) {
            System.out.println("WARNING: no heap or RAM figures will be recorded and the 90% memory limit is not checked.");
        }
        if (max > maxPlayers) {
            System.out.println("WARNING: max-players is " + maxPlayers + "; limiting --max to it.");
            max = maxPlayers;
        }
        String jar = opt("soulfire-jar", "");
        if (jar.isEmpty() || !Files.exists(Path.of(jar))) {
            throw new IllegalArgumentException("--soulfire-jar is missing; use run-load.ps1 or run-load.sh, which download it");
        }

        Path outDir = Path.of(opt("out-dir", "testing/results"));
        Files.createDirectories(outDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        Path csv = outDir.resolve("load-" + stamp + ".csv");
        Path summary = outDir.resolve("load-" + stamp + ".summary.txt");
        Path work = Path.of(opt("work-dir", "testing/.cache/load-work"));
        Files.createDirectories(work);
        soulfireLog = work.resolve("soulfire-" + stamp + ".log");
        Path accounts = work.resolve("accounts-" + stamp + ".txt");
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            names.add(prefix + String.format("%04d", i));
        }
        Files.write(accounts, names);

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("time,target_bots,bots_online,bots_lost,regions,util_pct,lowest_tps,median_tps,highest_tps,worst_mspt,worst_region_tps,"
                + "worst_region_players,heap_used_mb,heap_max_mb,heap_pct,gc_count,gc_time_ms,server_rss_mb,load_host_cpu_pct,mem_source,violations");
            Runtime.getRuntime().addShutdownHook(new Thread(LoadTest::stopBots));

            soulfire = startBots(jar, accounts, host, port, delay, opt("bot-ram", "2G"));
            int attempts = 1;
            System.out.println("SoulFire started (log: " + soulfireLog + "), bots join every " + delay + " ms.");

            String stop = null; // why the run ended; null means --max was reached
            int sustained = 0;
            int failedAt = 0;
            int peak = 0;
            String failDetail = "";
            List<Sample> all = new ArrayList<>();
            List<Integer> targets = new ArrayList<>();
            for (int t = step; t < max; t += step) {
                targets.add(t);
            }
            targets.add(max);
            Instant begin = Instant.now();

            outer:
            for (int target : targets) {
                // ---- wait for the bots to arrive
                while (online.size() < target) {
                    if (!soulfire.isAlive()) {
                        if (everJoined.isEmpty() && attempts < 3) {
                            attempts++;
                            System.out.println("SoulFire exited before any bot joined; starting it again (attempt " + attempts + ").");
                            soulfire = startBots(jar, accounts, host, port, delay, opt("bot-ram", "2G"));
                        } else {
                            stop = "the load generator (SoulFire) exited; the result is not a server limit";
                            break outer;
                        }
                    }
                    poll();
                    if (everJoined.size() - online.size() > tolerance) {
                        stop = "bots were kicked: " + (everJoined.size() - online.size()) + " of " + everJoined.size() + " lost";
                        failedAt = target;
                        failDetail = stop;
                        break outer;
                    }
                    if (System.currentTimeMillis() - lastGrowth > stall * 1000L && soulfire.isAlive()
                        && (!everJoined.isEmpty() || attempts >= 3 && System.currentTimeMillis() - lastGrowth > 3L * stall * 1000L)) {
                        stop = "bots stopped joining for " + stall + " s at " + online.size() + " (server full, whitelist, throttle or the load generator is saturated)";
                        failedAt = target;
                        failDetail = stop;
                        break outer;
                    }
                    Thread.sleep(optInt("poll-ms", 2000));
                }
                System.out.println("Step " + target + ": " + online.size() + " bots online, settling " + settle + " s.");
                long until = System.currentTimeMillis() + settle * 1000L;
                while (System.currentTimeMillis() < until) {
                    poll();
                    Thread.sleep(optInt("poll-ms", 2000));
                }

                // ---- measure
                int bad = 0;
                int heapHigh = 0;
                String detail = "";
                double prevGcMs = Double.NaN;
                for (int k = 0; k < samples; k++) {
                    long t0 = System.currentTimeMillis();
                    Sample s = sample();
                    all.add(s);
                    peak = Math.max(peak, s.online());
                    String v = violations(s, prevGcMs);
                    boolean mem90 = s.mem().heapMaxMb() > 0 && heapPct(s) >= 90
                        || Double.isNaN(s.mem().heapUsedMb()) && s.mem().rssMb() > 0 && !Double.isNaN(serverXmxMb) && s.mem().rssMb() >= 0.9 * serverXmxMb;
                    if (mem90) {
                        heapHigh++;
                    }
                    if (!v.isEmpty()) {
                        bad++;
                        detail = v;
                    }
                    Region wr = s.tps() == null ? null : s.tps().worst();
                    w.println(String.join(",", LocalTime.now().withNano(0).toString(), String.valueOf(target), String.valueOf(s.online()),
                        String.valueOf(s.kicked()), s.tps() == null ? "" : String.valueOf(s.tps().regions()), s.tps() == null ? "" : fmt(s.tps().util()),
                        s.tps() == null ? "" : fmt(s.tps().low()), s.tps() == null ? "" : fmt(s.tps().median()), s.tps() == null ? "" : fmt(s.tps().high()),
                        s.tps() == null ? "" : fmt(s.tps().worstMspt()), wr == null ? "" : fmt(wr.tps()), wr == null ? "" : String.valueOf(wr.players()),
                        fmt(s.mem().heapUsedMb()), fmt(s.mem().heapMaxMb()), Double.isNaN(s.mem().heapUsedMb()) ? "" : fmt(heapPct(s)),
                        fmt(s.mem().gcCount()), fmt(Double.isNaN(prevGcMs) || Double.isNaN(s.mem().gcTimeMs()) ? Double.NaN : s.mem().gcTimeMs() - prevGcMs),
                        fmt(s.mem().rssMb()), fmt(s.hostCpu()), s.mem().source(), v.replace(',', ';')));
                    w.flush();
                    prevGcMs = s.mem().gcTimeMs();
                    System.out.println("  sample " + (k + 1) + ": " + s.online() + " bots, " + (s.tps() == null ? "?" : s.tps().regions() + " regions, low TPS "
                        + fmt(s.tps().low()) + ", worst MSPT " + fmt(s.tps().worstMspt())) + ", heap " + fmt(heapPct(s)) + "%, host CPU " + fmt(s.hostCpu()) + "%"
                        + (v.isEmpty() ? "" : "  <-- " + v));
                    if (s.kicked() > tolerance) {
                        stop = "bots were kicked: " + s.kicked() + " of " + everJoined.size() + " lost";
                        failedAt = target;
                        failDetail = stop;
                        break outer;
                    }
                    long rest = hold * 1000L / samples - (System.currentTimeMillis() - t0);
                    if (rest > 0 && k < samples - 1) {
                        Thread.sleep(rest);
                    }
                }
                boolean heapFail = heapHigh == samples;
                if (bad * 2 >= samples || heapFail) {
                    failedAt = target;
                    failDetail = heapFail ? "heap above 90% of -Xmx in every sample" : detail;
                    stop = failDetail;
                    break;
                }
                sustained = target;
            }
            Duration took = Duration.between(begin, Instant.now());
            stopBots();

            // ---- summary
            StringBuilder sum = new StringBuilder();
            sum.append("LegacyFolia load test ").append(stamp).append("\n");
            sum.append("Target: ").append(host).append(':').append(port).append(", ").append(areas).append(" areas ").append(spacing).append(" blocks apart\n");
            sum.append("Server: max-players ").append(maxPlayers).append(", Xmx ").append(Double.isNaN(serverXmxMb) ? "unknown" : fmt(serverXmxMb) + " MB")
                .append(", memory source ").append(sparkAvailable ? "spark" : serverPid > 0 ? "jstat (local pid " + serverPid + ")" : "none").append("\n");
            sum.append("Load generator: SoulFire, -Xmx").append(opt("bot-ram", "2G")).append(", join every ").append(delay).append(" ms, ")
                .append(Runtime.getRuntime().availableProcessors()).append(" cores, ").append(fmt(OS.getTotalMemorySize() / 1073741824.0)).append(" GB RAM on this machine\n");
            sum.append("Step ").append(step).append(", hold ").append(hold).append(" s, ").append(samples).append(" samples per step; ran ")
                .append(took.toMinutes()).append(" min, peak ").append(peak).append(" bots online\n\n");
            double maxCpu = all.stream().mapToDouble(Sample::hostCpu).filter(d -> !Double.isNaN(d)).max().orElse(Double.NaN);
            if (stop == null) {
                sum.append("RESULT: no limit reached. Maximum sustained: ").append(sustained).append(" bots (the --max given); the server can take more than this.\n");
            } else if (stop.startsWith("the load generator")) {
                sum.append("RESULT: inconclusive, ").append(stop).append(". Last step that passed: ").append(sustained).append(" bots.\n");
            } else {
                sum.append("RESULT: maximum sustained ").append(sustained).append(" bots. Limit hit at ").append(failedAt).append(" bots: ").append(stop).append(".\n");
            }
            sum.append("Highest host CPU seen on the measuring machine: ").append(fmt(maxCpu)).append("% (a value near 100 means the bots and the server fought for the same CPU)\n");
            sum.append("CSV: ").append(csv).append("\nSoulFire log: ").append(soulfireLog).append("\n");
            Files.writeString(summary, sum.toString());
            System.out.println("\n" + sum);
        }
        rcon.close();
    }
}
