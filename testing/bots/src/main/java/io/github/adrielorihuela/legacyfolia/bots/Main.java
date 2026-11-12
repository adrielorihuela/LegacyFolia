package io.github.adrielorihuela.legacyfolia.bots;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        Path dir = Files.createTempDirectory("lf-smoke");
        Files.createDirectories(dir.resolve("config"));
        Files.writeString(dir.resolve("config/server.toml"), "[server]\neula = true\n");
        try (ServerProc s = ServerProc.startReady(jar, dir, 180_000, "--port", "25611", "--online-mode", "false")) {
            System.out.println("started in " + dir);
            Bot a = new Bot("BotA", "127.0.0.1", 25611);
            a.connect();
            System.out.println("A in world " + a.dimension + " at " + a.x + "," + a.y + "," + a.z + " id " + a.entityId);
            s.send("op BotA");
            Thread.sleep(500);
            System.out.println(s.cmdOut("list", 1000));
            System.out.println(s.cmdOut("data get entity @a[limit=1] Health", 1500));
            System.out.println(s.cmdOut("tp BotA 5 100 5", 1500));
            System.out.println(s.cmdOut("execute as BotA run say hi", 1500));
            System.out.println(s.tail(30));
            Thread.sleep(500);
            System.out.println("A at " + a.x + "," + a.y + "," + a.z + " health " + a.health);
            a.disconnect();
        }
    }
}
