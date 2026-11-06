package com.pacman3d.game;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Conexion TCP entre dos jugadores (anfitrion <-> cliente) con hilos de lectura y escritura. */
public final class Net {
    public static final int PORT = 7777, VERSION = 4;
    public static final byte HELLO = 1, START = 3, SNAP = 4, INPUT = 5, LEVEL = 6, RESTART = 7, BYE = 8, MENU = 9;

    public static final class Msg { public byte type; public byte[] data; }

    private final Socket sock;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<Msg> outbox = new LinkedBlockingQueue<>();
    public volatile boolean closed;

    public Net(Socket s) throws IOException {
        sock = s;
        s.setTcpNoDelay(true);
        in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
        out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 16));
        Thread r = new Thread(this::readLoop, "net-read"); r.setDaemon(true); r.start();
        Thread w = new Thread(this::writeLoop, "net-write"); w.setDaemon(true); w.start();
    }

    public static Net connect(String host, int port, int timeoutMs) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        return new Net(s);
    }

    private void readLoop() {
        try {
            while (!closed) {
                int len = in.readInt();
                if (len < 1 || len > 4_000_000) throw new IOException("paquete invalido");
                byte type = in.readByte();
                byte[] d = new byte[len - 1];
                in.readFully(d);
                Msg m = new Msg(); m.type = type; m.data = d;
                inbox.add(m);
            }
        } catch (Exception e) { closed = true; }
    }

    private void writeLoop() {
        try {
            while (!closed) {
                Msg m = outbox.take();
                out.writeInt(m.data.length + 1); out.writeByte(m.type); out.write(m.data);
                if (outbox.isEmpty()) out.flush();
            }
        } catch (Exception e) { closed = true; }
    }

    public void send(byte type, byte[] data) {
        if (closed) return;
        Msg m = new Msg(); m.type = type; m.data = data;
        if (type == SNAP && outbox.size() > 6) return;   // conexion lenta: se descarta una instantanea
        outbox.add(m);
    }

    public Msg poll() { return inbox.poll(); }

    public void close() {
        if (closed) { try { sock.close(); } catch (IOException ignored) { } return; }
        try { send(BYE, new byte[0]); Thread.sleep(80); } catch (Exception ignored) { }
        closed = true;
        try { sock.close(); } catch (IOException ignored) { }
    }

    /** Direcciones IP locales del equipo (para decirle al otro jugador a donde conectarse). */
    public static List<String> localAddresses() {
        List<String> l = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address) l.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) { }
        if (l.isEmpty()) l.add("127.0.0.1");
        return l;
    }

    // ---- utilidades de paquetes
    public static byte[] pack(Object... vals) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bo);
            for (Object v : vals) {
                if (v instanceof Integer) o.writeInt((Integer) v);
                else if (v instanceof Long) o.writeLong((Long) v);
                else if (v instanceof Float) o.writeFloat((Float) v);
                else if (v instanceof Boolean) o.writeBoolean((Boolean) v);
                else if (v instanceof Byte) o.writeByte((Byte) v);
                else if (v instanceof String) o.writeUTF((String) v);
            }
            return bo.toByteArray();
        } catch (IOException e) { return new byte[0]; }
    }
}
