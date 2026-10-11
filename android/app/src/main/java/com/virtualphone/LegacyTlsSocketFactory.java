package com.virtualphone;

import android.os.Build;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Creates a connected, host-aware SSLSocket for the legacy Java-WebSocket
 * client. The platform default trust manager validates the certificate chain,
 * and this class verifies the peer hostname before the WebSocket handshake.
 */
public final class LegacyTlsSocketFactory {
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private LegacyTlsSocketFactory() {}

    public static SSLSocket createSocket(String host, int port) throws IOException {
        if (host == null || host.trim().length() == 0 || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Valid TLS host and port are required");
        }

        Socket tcp = new Socket();
        try {
            tcp.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) delegate.createSocket(tcp, host, port, true);

            String[] supported = ssl.getSupportedProtocols();
            List<String> enabled = new ArrayList<String>();

            /*
             * API 1–15 lack a modern TLS stack. When a device owner has
             * configured a transparent legacy TLS proxy such as Wolfius,
             * the app-side connection must be able to offer TLS 1.0 to that
             * local proxy. Certificate-chain and hostname checks remain on.
             * Without a configured proxy, modern hosts will reject TLS 1.0.
             */
            String[] preferred = apiLevel() < 16
                    ? new String[] {"TLSv1"}
                    : new String[] {"TLSv1.3", "TLSv1.2"};
            for (String wanted : preferred) {
                for (String actual : supported) {
                    if (wanted.equals(actual)) {
                        enabled.add(wanted);
                        break;
                    }
                }
            }

            if (enabled.isEmpty()) {
                try { ssl.close(); } catch (IOException ignored) {}
                throw new IOException(
                        "No permitted TLS protocol is available. Android 1–3 " +
                        "requires a configured legacy TLS proxy; TLS certificate and " +
                        "hostname validation are not disabled."
                );
            }

            ssl.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));

            // Do TLS and hostname validation BEFORE WebSocketClient can send
            // an HTTP Upgrade request or receive any WebSocket messages.
            ssl.setSoTimeout(CONNECT_TIMEOUT_MS);
            ssl.startHandshake();
            SSLSession session = ssl.getSession();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) {
                throw new SSLHandshakeException("TLS hostname verification failed");
            }
            ssl.setSoTimeout(0);
            return ssl;
        } catch (IOException e) {
            try { tcp.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    private static int apiLevel() {
        try {
            return Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception ignored) {
            String release = Build.VERSION.RELEASE;
            if ("1.0".equals(release)) return 1;
            if ("1.1".equals(release)) return 2;
            if ("1.5".equals(release)) return 3;
            if ("1.6".equals(release)) return 4;
            if ("2.0.1".equals(release)) return 6;
            if ("2.0".equals(release)) return 5;
            if ("2.1".equals(release)) return 7;
            if ("2.2".equals(release)) return 8;
            if ("2.3".equals(release) || "2.3.1".equals(release)
                    || "2.3.2".equals(release)) return 9;
            if (release != null && release.startsWith("2.3")) return 10;
            if ("3.0".equals(release)) return 11;
            if ("3.1".equals(release)) return 12;
            if (release != null && release.startsWith("3.2")) return 13;
            return 1;
        }
    }
}
