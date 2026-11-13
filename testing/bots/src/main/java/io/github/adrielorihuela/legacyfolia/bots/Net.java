package io.github.adrielorihuela.legacyfolia.bots;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Raw clients for the two small protocols the server checks need: server list ping and RCON. */
final class Net {
    private Net() {
    }

    private static void varInt(DataOutputStream o, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            o.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int n = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            n |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return n;
    }

    /** The raw JSON of the server list response, or null when nothing answers. */
    static String status(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            varInt(h, 0);
            varInt(h, 775);
            byte[] hb = host.getBytes(StandardCharsets.UTF_8);
            varInt(h, hb.length);
            h.write(hb);
            h.writeShort(port);
            varInt(h, 1);
            varInt(out, hs.size());
            out.write(hs.toByteArray());
            varInt(out, 1);
            out.writeByte(0);
            out.flush();
            DataInputStream in = new DataInputStream(s.getInputStream());
            readVarInt(in);
            readVarInt(in);
            int len = readVarInt(in);
            byte[] b = new byte[len];
            in.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** The plain MOTD text from a status JSON ("description" as a string or {"text":...}). */
    static String motd(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"description\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("\"description\":\\s*\\{[^}]*?\"text\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** True when something accepts a TCP connection. */
    static boolean open(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Logs in over RCON and runs one command: returns its answer, or "AUTH-FAILED", or null if nothing listens. */
    static String rcon(String host, int port, String password, String command) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(5000);
            send(s, 1, 3, password);
            int[] r = readHeader(s);
            String body = readBody(s, r[0]);
            if (r[1] == -1) {
                return "AUTH-FAILED";
            }
            send(s, 2, 2, command);
            int[] r2 = readHeader(s);
            return readBody(s, r2[0]);
        } catch (IOException e) {
            return null;
        }
    }

    private static void send(Socket s, int id, int type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(14 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(10 + b.length).putInt(id).putInt(type).put(b).put((byte) 0).put((byte) 0);
        s.getOutputStream().write(buf.array());
        s.getOutputStream().flush();
    }

    /** Returns {payload length, request id}. */
    private static int[] readHeader(Socket s) throws IOException {
        byte[] h = s.getInputStream().readNBytes(12);
        if (h.length < 12) {
            throw new IOException("short rcon header");
        }
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int len = b.getInt();
        int id = b.getInt();
        return new int[] {len - 8, id};
    }

    private static String readBody(Socket s, int n) throws IOException {
        byte[] body = s.getInputStream().readNBytes(n);
        return new String(body, 0, Math.max(0, body.length - 2), StandardCharsets.UTF_8);
    }
}
