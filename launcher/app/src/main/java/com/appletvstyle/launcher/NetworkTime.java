package com.appletvstyle.launcher;

import android.content.Context;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 轻量网络对时：这台 KKTV 固件的系统 NTP 同步是坏的（重启后停在 1970），
 * 桌面时钟不能跟着系统时间错。先试 UDP SNTP（多数家用网络挡 123 端口），
 * 失败则回退到 HTTP 响应头的 Date 字段（TCP 80，穿透性最好）。
 * 算出「网络 - 系统」偏移后，显示统一用校正时间；失败静默回退系统时间。
 */
final class NetworkTime {
    private NetworkTime() {}

    private static final String[] SNTP_SERVERS = {
            "ntp.aliyun.com", "cn.pool.ntp.org", "pool.ntp.org", "time.windows.com"
    };
    private static final String[] HTTP_SOURCES = {
            "http://www.baidu.com", "http://www.qq.com", "http://www.taobao.com"
    };
    private static final long MIN_PLAUSIBLE = 1577836800000L; // 2020-01-01
    private static final long MAX_PLAUSIBLE = 2208988800000L; // 2040-01-01

    private static volatile long offsetMillis;
    private static volatile long lastSync;
    private static volatile boolean syncing;
    /** 对时成功后在主线程回调（宿主用来立即重绘时钟）；静态引用，宿主 onDestroy 时置空。 */
    static Runnable onSynced;

    /** 校正后的当前时间。 */
    static long now() {
        return System.currentTimeMillis() + offsetMillis;
    }

    static boolean synced() {
        return lastSync != 0;
    }

    /** 异步对时；成功前 offset 保持 0（即显示系统时间）。 */
    static void syncAsync(final Context c) {
        if (syncing) return;
        syncing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Long network = null;
                String via = null;
                for (String server : SNTP_SERVERS) {
                    network = querySntp(server);
                    if (network != null) {
                        via = "sntp:" + server;
                        break;
                    }
                }
                if (network == null) {
                    for (String url : HTTP_SOURCES) {
                        network = queryHttp(url);
                        if (network != null) {
                            via = "http:" + url;
                            break;
                        }
                    }
                }
                if (network != null) {
                    // 系统时钟可能停在 1970（这台固件的 NTP 是坏的），偏移可达数十年，
                    // 所以 sanity 检查落在「网络时间本身是否合理」而不是偏移大小。
                    if (network >= MIN_PLAUSIBLE && network <= MAX_PLAUSIBLE) {
                        long offset = network - System.currentTimeMillis();
                        offsetMillis = offset;
                        lastSync = System.currentTimeMillis();
                        Log.i("LC", "network time ok via " + via + " offset=" + offset + "ms");
                        if (onSynced != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onSynced);
                        }
                    } else {
                        Log.w("LC", "network time implausible=" + network + " via " + via);
                    }
                } else {
                    Log.w("LC", "network time unavailable (sntp+http all failed)");
                }
                syncing = false;
            }
        }, "nettime").start();
    }

    /** UDP SNTP：返回服务器当前时间（ms），失败返回 null。 */
    private static Long querySntp(String server) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(2500);
            InetAddress address = InetAddress.getByName(server);
            byte[] buffer = new byte[48];
            buffer[0] = 0x1B; // LI=0, VN=3, Mode=3(client)
            DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, 123);
            socket.send(request);
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            long seconds = readUnsignedInt(buffer, 40);   // 传输时间戳（1900 起秒）
            long fraction = readUnsignedInt(buffer, 44);
            return (seconds - 2208988800L) * 1000 + ((fraction * 1000L) >>> 32);
        } catch (Exception e) {
            Log.w("LC", "sntp " + server + " failed: " + e);
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    /** HTTP Date 头：返回服务器当前时间（ms），失败返回 null。 */
    private static Long queryHttp(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.connect();
            String date = conn.getHeaderField("Date");
            if (date == null) return null;
            SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT"));
            return f.parse(date).getTime();
        } catch (Exception e) {
            Log.w("LC", "http-time " + endpoint + " failed: " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long readUnsignedInt(byte[] b, int offset) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (b[offset + i] & 0xFFL);
        }
        return value;
    }
}
