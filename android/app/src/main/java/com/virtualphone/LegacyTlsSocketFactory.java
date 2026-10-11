package com.virtualphone;

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
            String[] preferred = new String[] {"TLSv1.3", "TLSv1.2"};
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
                        "This Android TLS provider lacks TLS 1.2 or newer. " +
                        "A separately audited legacy TLS provider is required."
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
}
