package io.github.adrielorihuela.legacyfolia.bots;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.TrackedWaypoint;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.WaypointOperation;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundHurtAnimationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundTrackedWaypointPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

/**
 * A scripted Minecraft 26.3 client. It does what a real client does to stay on the server (keep-alives, teleport
 * and chunk acknowledgements, a position every tick) and records the packets the tests need. The server stays
 * the authority: a bot never simulates physics, tests place it with /tp and move it with explicit packets.
 */
final class Bot {
    record Timed<T>(long at, T value) {
    }

    record Motion(int entity, Vector3d v, long at) {
    }

    record WpEvent(long at, WaypointOperation op, TrackedWaypoint wp) {
    }

    record EffectEv(long at, int entity, String effect, int amp, int duration) {
    }

    record AddEv(long at, int id, EntityType type, UUID uuid, double x, double y, double z, Vector3d movement) {
    }

    record SlotEv(long at, int container, int slot, ItemStack item) {
    }

    final String name;
    final UUID uuid;
    final String host;
    final int port;
    ClientSessionHolder holder;
    volatile boolean play;
    volatile boolean autoTick = true;
    volatile boolean autoRespawn = true;
    volatile String disconnectReason;
    volatile int entityId = -1;
    volatile String dimension = "";
    volatile double x, y, z;
    volatile float yaw, pitch;
    volatile boolean onGround = true;
    volatile float health = 20;
    volatile int food = 20;
    volatile boolean dead;
    volatile long positionPackets;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bot-tick");
        t.setDaemon(true);
        return t;
    });

    final List<Timed<Float>> healthEvents = Collections.synchronizedList(new ArrayList<>());
    final List<Motion> motions = Collections.synchronizedList(new ArrayList<>());
    final List<WpEvent> waypoints = Collections.synchronizedList(new ArrayList<>());
    final List<EffectEv> effects = Collections.synchronizedList(new ArrayList<>());
    final List<AddEv> adds = Collections.synchronizedList(new ArrayList<>());
    final List<SlotEv> slotEvents = Collections.synchronizedList(new ArrayList<>());
    final List<String> teams = Collections.synchronizedList(new ArrayList<>());
    final List<Timed<String>> chat = Collections.synchronizedList(new ArrayList<>());
    final List<Timed<String>> dimensionChanges = Collections.synchronizedList(new ArrayList<>());
    /** (entity id, kind) for damage events, hurt animations and entity events. */
    final List<Timed<String>> hurts = Collections.synchronizedList(new ArrayList<>());
    final Map<Integer, ItemStack> slots = new ConcurrentHashMap<>();
    final Map<Integer, Map<EquipmentSlot, ItemStack>> equipment = new ConcurrentHashMap<>();
    final Map<Integer, ItemStack> entityItems = new ConcurrentHashMap<>();
    final List<Timed<ClientboundMerchantOffersPacket>> trades = Collections.synchronizedList(new ArrayList<>());
    final List<Timed<ClientboundContainerSetContentPacket>> contents = Collections.synchronizedList(new ArrayList<>());
    final List<Timed<ClientboundOpenScreenPacket>> screens = Collections.synchronizedList(new ArrayList<>());
    int lastTeleportId;

    /** Wraps the session so the field can be assigned after construction. */
    static final class ClientSessionHolder {
        org.geysermc.mcprotocollib.network.ClientSession session;
    }

    Bot(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    static long now() {
        return System.currentTimeMillis();
    }

    // ---- connection ----

    /** Connects and waits for the first position packet. */
    void connect() throws Exception {
        play = false;
        dead = false;
        disconnectReason = null;
        MinecraftProtocol protocol = new MinecraftProtocol(name);
        holder = new ClientSessionHolder();
        holder.session = ClientNetworkSessionFactory.factory().setAddress(host, port).setProtocol(protocol).create();
        holder.session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                try {
                    handle(packet);
                } catch (RuntimeException e) {
                    System.err.println("[bot " + name + "] handler error on " + packet.getClass().getSimpleName() + ": " + e);
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                play = false;
                if (disconnectReason == null) {
                    disconnectReason = plain(event.getReason());
                }
            }
        });
        holder.session.connect(true);
        long end = now() + 30_000;
        while (positionPackets == 0 && now() < end && disconnectReason == null) {
            Thread.sleep(25);
        }
        if (positionPackets == 0) {
            throw new IllegalStateException(name + " did not get into the world: " + (disconnectReason == null ? "no position packet in 30s" : disconnectReason));
        }
        ticker.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
    }

    boolean connected() {
        return holder != null && holder.session.isConnected() && play;
    }

    void disconnect() {
        play = false;
        if (holder != null) {
            try {
                holder.session.disconnect("bye");
            } catch (RuntimeException ignored) {
            }
        }
        ticker.shutdownNow();
    }

    void send(Packet p) {
        if (holder != null && holder.session.isConnected()) {
            holder.session.send(p);
        }
    }

    private void tick() {
        if (!play || !autoTick || dead || System.getenv("BOT_NOTICK") != null) {
            return;
        }
        if (System.getenv("BOT_DEBUG") != null && (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch))) {
            System.err.println("[bot " + name + "] BAD tick " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        }
        sendPos(false);
    }

    /** Ends the client tick; the server resets its once-per-tick position check on it. */
    private void tickEnd() {
        send(org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket.INSTANCE);
    }

    private long lastPos;
    private double sx = Double.NaN, sy, sz;
    private float syaw, spitch;
    private int ticks;

    /**
     * Like the real client: a position packet only when the position changed (or every 20 ticks), a rotation or
     * status packet otherwise. The server kicks a client that sends two position packets in one tick, so they are
     * kept 55 ms apart and a blocking caller waits for its turn.
     */
    private synchronized void sendPos(boolean block) {
        ticks++;
        boolean moved = Double.isNaN(sx) || sx != x || sy != y || sz != z || ticks % 20 == 0;
        if (moved) {
            long wait = lastPos + 55 - now();
            if (wait > 0) {
                if (!block) {
                    return;
                }
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastPos = now();
            sx = x;
            sy = y;
            sz = z;
            syaw = yaw;
            spitch = pitch;
            send(new ServerboundMovePlayerPosRotPacket(onGround, false, x, y, z, yaw, pitch));
            tickEnd();
        } else if (syaw != yaw || spitch != pitch) {
            syaw = yaw;
            spitch = pitch;
            send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket(onGround, false, yaw, pitch));
            tickEnd();
        } else {
            send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket(onGround, false));
            tickEnd();
        }
    }

    // ---- packet handling ----

    private void handle(Packet packet) {
        long t = now();
        switch (packet) {
            case ClientboundLoginPacket p -> {
                entityId = p.getEntityId();
                dimension = p.getCommonPlayerSpawnInfo().getWorldName().asString();
                dimensionChanges.add(new Timed<>(t, dimension));
                play = true;
            }
            case ClientboundRespawnPacket p -> {
                dimension = p.getCommonPlayerSpawnInfo().getWorldName().asString();
                dimensionChanges.add(new Timed<>(t, dimension));
                dead = false;
            }
            case ClientboundPlayerPositionPacket p -> {
                Vector3d pos = p.getPosition();
                var rel = p.getRelatives();
                if (System.getenv("BOT_DEBUG") != null) {
                    System.err.println("[bot " + name + "] position " + p);
                }
                boolean rx = rel.stream().anyMatch(e -> e.name().equals("X"));
                boolean ry = rel.stream().anyMatch(e -> e.name().equals("Y"));
                boolean rz = rel.stream().anyMatch(e -> e.name().equals("Z"));
                x = rx ? x + pos.getX() : pos.getX();
                y = ry ? y + pos.getY() : pos.getY();
                z = rz ? z + pos.getZ() : pos.getZ();
                if (rel.stream().noneMatch(e -> e.name().equals("Y_ROT"))) {
                    yaw = p.getYRot();
                }
                if (rel.stream().noneMatch(e -> e.name().equals("X_ROT"))) {
                    pitch = p.getXRot();
                }
                lastTeleportId = p.getId();
                send(new ServerboundAcceptTeleportationPacket(p.getId(), x, y, z, yaw, pitch));
                onGround = true;
                if (positionPackets == 0) {
                    send(ServerboundPlayerLoadedPacket.INSTANCE);
                }
                positionPackets++;
            }
            case ClientboundChunkBatchFinishedPacket p -> send(new ServerboundChunkBatchReceivedPacket(20f));
            case ClientboundSetHealthPacket p -> {
                health = p.getHealth();
                food = p.getFood();
                healthEvents.add(new Timed<>(t, p.getHealth()));
                if (p.getHealth() <= 0) {
                    dead = true;
                    if (autoRespawn) {
                        send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
                    }
                }
            }
            case ClientboundPlayerCombatKillPacket p -> {
                dead = true;
                if (autoRespawn) {
                    send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
                }
            }
            case ClientboundSetEntityMotionPacket p -> motions.add(new Motion(p.getEntityId(), p.getMovement(), t));
            case ClientboundTrackedWaypointPacket p -> waypoints.add(new WpEvent(t, p.getOperation(), p.getWaypoint()));
            case ClientboundUpdateMobEffectPacket p -> effects.add(new EffectEv(t, p.getEntityId(), p.getEffect().name(), p.getAmplifier(), p.getDuration()));
            case ClientboundAddEntityPacket p -> adds.add(new AddEv(t, p.getEntityId(), p.getType(), p.getUuid(), p.getX(), p.getY(), p.getZ(), p.getMovement()));
            case ClientboundRemoveEntitiesPacket p -> {
                for (int id : p.getEntityIds()) {
                    entityItems.remove(id);
                    equipment.remove(id);
                }
            }
            case ClientboundDamageEventPacket p -> hurts.add(new Timed<>(t, p.getEntityId() + ":damage"));
            case ClientboundHurtAnimationPacket p -> hurts.add(new Timed<>(t, p.getId() + ":hurt"));
            case ClientboundEntityEventPacket p -> hurts.add(new Timed<>(t, p.getEntityId() + ":event:" + p.getEvent()));
            case org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket p -> teams.add(p.toString());
            case ClientboundSystemChatPacket p -> chat.add(new Timed<>(t, plain(p.getContent())));
            case ClientboundContainerSetSlotPacket p -> {
                slotEvents.add(new SlotEv(t, p.getContainerId(), p.getSlot(), p.getItem()));
                if (p.getContainerId() == 0) {
                    if (p.getItem() == null) {
                        slots.remove(p.getSlot());
                    } else {
                        slots.put(p.getSlot(), p.getItem());
                    }
                }
            }
            case ClientboundContainerSetContentPacket p -> {
                contents.add(new Timed<>(t, p));
                ItemStack[] items = p.getItems();
                for (int i = 0; i < items.length; i++) {
                    slotEvents.add(new SlotEv(t, p.getContainerId(), i, items[i]));
                    if (p.getContainerId() == 0) {
                        if (items[i] == null) {
                            slots.remove(i);
                        } else {
                            slots.put(i, items[i]);
                        }
                    }
                }
            }
            case ClientboundOpenScreenPacket p -> screens.add(new Timed<>(t, p));
            case ClientboundMerchantOffersPacket p -> trades.add(new Timed<>(t, p));
            case ClientboundSetEquipmentPacket p -> {
                Map<EquipmentSlot, ItemStack> m = equipment.computeIfAbsent(p.getEntityId(), k -> new ConcurrentHashMap<>());
                for (Equipment e : p.getEquipment()) {
                    if (e.getItem() == null) {
                        m.remove(e.getSlot());
                    } else {
                        m.put(e.getSlot(), e.getItem());
                    }
                }
            }
            case ClientboundSetEntityDataPacket p -> {
                for (EntityMetadata<?, ?> md : p.getMetadata()) {
                    if (md.getValue() instanceof ItemStack is) {
                        entityItems.put(p.getEntityId(), is);
                    }
                }
            }
            case ClientboundDisconnectPacket p -> disconnectReason = plain(p.getReason());
            default -> {
            }
        }
    }

    static String plain(Component c) {
        if (c == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (c instanceof TextComponent tc) {
            sb.append(tc.content());
        } else if (c instanceof TranslatableComponent tr) {
            sb.append(tr.key());
            for (var a : tr.arguments()) {
                sb.append(' ').append(plain(a.asComponent()));
            }
        }
        for (Component child : c.children()) {
            sb.append(plain(child));
        }
        return sb.toString();
    }

    // ---- actions ----

    void attack(int entity) {
        send(new ServerboundAttackPacket(entity));
    }

    void interact(int entity) {
        send(new ServerboundInteractPacket(entity, Hand.MAIN_HAND, Vector3d.from(0, 0, 0), false));
    }

    void use(Hand hand) {
        send(new ServerboundUseItemPacket(hand, 0, yaw, pitch));
    }

    void releaseUse() {
        send(new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, 0));
    }

    void swapHands() {
        send(new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0));
    }

    void useBlock(int bx, int by, int bz) {
        send(new ServerboundUseItemOnPacket(Vector3i.from(bx, by, bz), Direction.UP, Hand.MAIN_HAND, 0.5f, 1f, 0.5f, false, false, 0));
    }

    void select(int hotbar) {
        send(new ServerboundSetCarriedItemPacket(hotbar));
    }

    /** Crouching is an input flag in 26.3. */
    void sneak(boolean on) {
        send(new ServerboundPlayerInputPacket(false, false, false, false, false, on, false));
    }

    void sprint(boolean on) {
        send(new ServerboundPlayerInputPacket(on, false, false, false, false, false, on));
        send(new ServerboundPlayerCommandPacket(entityId, on ? PlayerState.START_SPRINTING : PlayerState.STOP_SPRINTING));
    }

    void creativeSlot(int slot, ItemStack item) {
        send(new ServerboundSetCreativeModeSlotPacket((short) slot, item));
    }

    /** Looks towards a point, as a client would for a click aimed at it. */
    void lookAt(double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + 1.62), dz = tz - z;
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    /** Sends one movement packet right now, used by tests that script a fall. */
    void moveRaw(double nx, double ny, double nz, boolean ground) {
        x = nx;
        y = ny;
        z = nz;
        onGround = ground;
        sendPos(true);
    }

    // ---- reading ----

    boolean waitFor(Predicate<Bot> p, long ms) throws InterruptedException {
        long end = now() + ms;
        while (now() < end) {
            if (p.test(this)) {
                return true;
            }
            Thread.sleep(20);
        }
        return p.test(this);
    }

    /** Last waypoint state of a transmitter: null if untracked or never seen, else the last event. */
    WpEvent waypointOf(UUID id) {
        WpEvent last = null;
        synchronized (waypoints) {
            for (WpEvent e : waypoints) {
                if (e.wp().uuid().equals(id)) {
                    last = e;
                }
            }
        }
        return last == null || last.op() == WaypointOperation.UNTRACK ? null : last;
    }

    List<WpEvent> waypointEventsSince(long since, UUID id) {
        List<WpEvent> out = new ArrayList<>();
        synchronized (waypoints) {
            for (WpEvent e : waypoints) {
                if (e.at() >= since && e.wp().uuid().equals(id)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** Last velocity the server sent for this bot's own entity since a moment, or null. */
    Vector3d lastOwnMotionSince(long since) {
        Vector3d last = null;
        synchronized (motions) {
            for (Motion m : motions) {
                if (m.entity() == entityId && m.at() >= since) {
                    last = m.v();
                }
            }
        }
        return last;
    }

    static double horizontal(Vector3d v) {
        return Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }
}
