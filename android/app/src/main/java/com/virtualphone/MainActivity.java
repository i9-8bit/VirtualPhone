package com.virtualphone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

public class MainActivity extends Activity {

  // =========================
  // BACKEND / CALL VARIABLES
  // =========================

  EditText server, userId, peerId;
  String savedServerUrl = "";
  String savedUserId = "";
  TextView status;
  TextView connectionBadge;
  TextView pageTitle;
  TextView callInfo;

  WebSocketClient ws;
  LegacyAudio audio;
  String callId;

  ArrayList<String[]> messages = new ArrayList<>();

  String activeChatUser = "";

  LinearLayout messageList;
  EditText messageRecipient;
  EditText messageInput;

  LinearLayout pageContainer;
  int currentPage = 0;

  // =========================
  // COLORS
  // =========================

  final int BG = Color.rgb(9, 11, 18);
  final int CARD = Color.rgb(20, 23, 33);
  final int CARD2 = Color.rgb(27, 31, 43);
  final int TEXT = Color.WHITE;
  final int MUTED = Color.rgb(155, 163, 180);
  final int ACCENT = Color.rgb(80, 180, 255);
  final int GREEN = Color.rgb(70, 210, 125);
  final int RED = Color.rgb(245, 85, 95);
  final int PURPLE = Color.rgb(155, 110, 255);

  // =========================
  // ACTIVITY
  // =========================

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);

    // These Window color APIs were added in Android 5.0 (API 21).
    // The Galaxy S3 on Android 4.3 must skip them.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(BG);
      getWindow().setNavigationBarColor(BG);
    }

    buildApp();

    if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

      requestPermissions(
              new String[]{Manifest.permission.RECORD_AUDIO},
              7
      );
    }
  }

  // =========================
  // BUILD ENTIRE UI
  // =========================

  void buildApp() {

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);

    // ---------- TOP ----------
    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(22), dp(16), dp(22), dp(12));

    LinearLayout titleBox = new LinearLayout(this);
    titleBox.setOrientation(LinearLayout.VERTICAL);

    TextView appName = text(
            "VirtualPhone",
            25,
            TEXT,
            Typeface.BOLD
    );

    pageTitle = text(
            "Home",
            13,
            MUTED,
            Typeface.NORMAL
    );

    titleBox.addView(appName);
    titleBox.addView(pageTitle);

    top.addView(
            titleBox,
            new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            )
    );

    connectionBadge = text(
            "OFFLINE",
            11,
            MUTED,
            Typeface.BOLD
    );

    connectionBadge.setGravity(Gravity.CENTER);
    connectionBadge.setPadding(
            dp(12), dp(7), dp(12), dp(7)
    );

    connectionBadge.setBackground(
            rounded(Color.rgb(40, 43, 55), 50)
    );

    top.addView(connectionBadge);

    root.addView(top);

    // ---------- CONTENT ----------
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);

    pageContainer = new LinearLayout(this);
    pageContainer.setOrientation(LinearLayout.VERTICAL);
    pageContainer.setPadding(
            dp(18), 0, dp(18), dp(20)
    );

    scroll.addView(pageContainer);

    root.addView(
            scroll,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1
            )
    );

    // ---------- BOTTOM NAV ----------
    LinearLayout nav = new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setGravity(Gravity.CENTER);
    nav.setPadding(
            dp(8), dp(8), dp(8), dp(10)
    );
    nav.setBackgroundColor(
            Color.rgb(14, 16, 24)
    );

    String[] navNames = {
            "Home",
            "Calls",
            "Messages",
            "Contacts",
            "Dialer",
            "Settings"
    };

    for (int i = 0; i < navNames.length; i++) {
      final int page = i;

      Button b = new Button(this);
      b.setText(navNames[i]);
      b.setTextSize(11);
      b.setTextColor(MUTED);
      b.setAllCaps(false);
      b.setPadding(0, 0, 0, 0);
      b.setBackgroundColor(Color.TRANSPARENT);

      b.setOnClickListener(v -> showPage(page));

      nav.addView(
              b,
              new LinearLayout.LayoutParams(
                      0,
                      dp(58),
                      1
              )
      );
    }

    root.addView(nav);

    setContentView(root);

    showPage(0);
  }

  // =========================
  // PAGE SWITCHING
  // =========================

  void showPage(int page) {

    if (server != null) {
      savedServerUrl = server.getText().toString();
    }
    if (userId != null) {
      savedUserId = userId.getText().toString();
    }

    currentPage = page;
    pageContainer.removeAllViews();

    switch (page) {
      case 0:
        pageTitle.setText("Home");
        homePage();
        break;

      case 1:
        pageTitle.setText("Calls");
        callsPage();
        break;

      case 2:
        pageTitle.setText("Messages");
        messagesPage();
        break;

      case 3:
        pageTitle.setText("Contacts");
        contactsPage();
        break;

      case 4:
        pageTitle.setText("Dialer");
        dialerPage();
        break;

      case 5:
        pageTitle.setText("Settings");
        settingsPage();
        break;
    }
  }

  // =========================
  // HOME PAGE
  // =========================

  void homePage() {

    addSpace(6);

    TextView welcome = text(
            "Your phone,\nconnected.",
            32,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(welcome);

    addSpace(8);

    TextView subtitle = text(
            "Make calls through your VirtualPhone server.",
            15,
            MUTED,
            Typeface.NORMAL
    );

    pageContainer.addView(subtitle);

    addSpace(20);

    // Connection card
    LinearLayout connection = card();

    TextView ct = text(
            "Connection",
            18,
            TEXT,
            Typeface.BOLD
    );

    connection.addView(ct);

    addSpaceTo(connection, 8);

    status = text(
            "Not connected",
            15,
            MUTED,
            Typeface.NORMAL
    );

    connection.addView(status);

    addSpaceTo(connection, 14);

    Button connectButton = actionButton(
            "Connect to server",
            ACCENT
    );

    connectButton.setOnClickListener(v -> connect());

    connection.addView(connectButton);

    pageContainer.addView(connection);

    addSpace(14);

    // Quick call card
    LinearLayout quick = card();

    TextView qt = text(
            "Quick Call",
            18,
            TEXT,
            Typeface.BOLD
    );

    quick.addView(qt);

    addSpaceTo(quick, 8);

    TextView qsub = text(
            "Enter a user ID and start a call instantly.",
            14,
            MUTED,
            Typeface.NORMAL
    );

    quick.addView(qsub);

    addSpaceTo(quick, 12);

    peerId = input(
            "Phone number / User ID"
    );

    quick.addView(
            peerId,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    addSpaceTo(quick, 12);

    Button callButton = actionButton(
            "☎  Call",
            GREEN
    );

    callButton.setOnClickListener(v -> call());

    quick.addView(callButton);

    pageContainer.addView(quick);

    addSpace(14);

    // Current call card
    LinearLayout current = card();

    TextView activeTitle = text(
            "Current Call",
            18,
            TEXT,
            Typeface.BOLD
    );

    current.addView(activeTitle);

    addSpaceTo(current, 8);

    callInfo = text(
            callId == null
                    ? "No active call"
                    : "Call in progress",
            15,
            MUTED,
            Typeface.NORMAL
    );

    current.addView(callInfo);

    addSpaceTo(current, 14);

    Button hangButton = actionButton(
            "End Call",
            RED
    );

    hangButton.setOnClickListener(v -> hang());

    current.addView(hangButton);

    pageContainer.addView(current);
  }

  // =========================
  // CALLS PAGE
  // =========================

  void callsPage() {

    addSpace(6);

    TextView heading = text(
            "Calls",
            30,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(heading);

    addSpace(6);

    TextView sub = text(
            "Your call activity will appear here.",
            14,
            MUTED,
            Typeface.NORMAL
    );

    pageContainer.addView(sub);

    addSpace(18);

    LinearLayout active = card();

    TextView title = text(
            "Active Call",
            18,
            TEXT,
            Typeface.BOLD
    );

    active.addView(title);

    addSpaceTo(active, 10);

    TextView activeStatus = text(
            callId == null
                    ? "No active call"
                    : "Call connected",
            15,
            MUTED,
            Typeface.NORMAL
    );

    active.addView(activeStatus);

    addSpaceTo(active, 15);

    Button hang = actionButton(
            "End Call",
            RED
    );

    hang.setOnClickListener(v -> hang());

    active.addView(hang);

    pageContainer.addView(active);

    addSpace(14);

    LinearLayout history = card();

    TextView historyTitle = text(
            "Recent",
            18,
            TEXT,
            Typeface.BOLD
    );

    history.addView(historyTitle);

    addSpaceTo(history, 14);

    TextView empty = text(
            "No recent calls yet.",
            14,
            MUTED,
            Typeface.NORMAL
    );

    history.addView(empty);

    pageContainer.addView(history);
  }

  // =========================
  // CONTACTS PAGE
  // =========================
  void messagesPage() {

    addSpace(6);

    TextView heading = text(
            "Messages",
            30,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(heading);

    addSpace(6);

    TextView sub = text(
            "Chat with another VirtualPhone user.",
            14,
            MUTED,
            Typeface.NORMAL
    );

    pageContainer.addView(sub);

    addSpace(14);

    messageRecipient = input("Recipient User ID");

    if (!activeChatUser.isEmpty()) {
      messageRecipient.setText(activeChatUser);
    }

    pageContainer.addView(
            messageRecipient,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    addSpace(12);

    LinearLayout chatCard = card();

    TextView chatTitle = text(
            activeChatUser.isEmpty()
                    ? "Conversation"
                    : "Chat with " + activeChatUser,
            18,
            TEXT,
            Typeface.BOLD
    );

    chatCard.addView(chatTitle);

    addSpaceTo(chatCard, 12);

    messageList = new LinearLayout(this);
    messageList.setOrientation(
            LinearLayout.VERTICAL
    );

    chatCard.addView(
            messageList,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
    );

    pageContainer.addView(chatCard);

    refreshMessages();

    addSpace(12);

    messageInput = input("Type a message");
    messageInput.setSingleLine(false);
    messageInput.setGravity(Gravity.TOP);
    messageInput.setPadding(
            dp(16),
            dp(14),
            dp(16),
            dp(14)
    );

    pageContainer.addView(
            messageInput,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(90)
            )
    );

    addSpace(10);

    Button sendButton =
            actionButton("Send Message", ACCENT);

    sendButton.setOnClickListener(
            v -> sendTextMessage()
    );

    pageContainer.addView(sendButton);
  }
  void contactsPage() {

    addSpace(6);

    TextView heading = text(
            "Contacts",
            30,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(heading);

    addSpace(14);

    EditText search = input("Search contacts");

    pageContainer.addView(
            search,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    addSpace(16);

    LinearLayout contact = card();

    TextView avatar = text(
            "V",
            28,
            TEXT,
            Typeface.BOLD
    );

    avatar.setGravity(Gravity.CENTER);
    avatar.setBackground(
            rounded(PURPLE, 100)
    );

    LinearLayout.LayoutParams avatarParams =
            new LinearLayout.LayoutParams(
                    dp(58),
                    dp(58)
            );

    contact.addView(avatar, avatarParams);

    LinearLayout info = new LinearLayout(this);
    info.setOrientation(LinearLayout.VERTICAL);
    info.setPadding(dp(14), 0, 0, 0);

    TextView name = text(
            "VirtualPhone User",
            17,
            TEXT,
            Typeface.BOLD
    );

    TextView id = text(
            "Use the Dialer to enter a User ID",
            13,
            MUTED,
            Typeface.NORMAL
    );

    info.addView(name);
    addSpaceTo(info, 3);
    info.addView(id);

    contact.addView(
            info,
            new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            )
    );

    pageContainer.addView(contact);

    addSpace(14);

    TextView hint = text(
            "Contacts can be connected to your server later.",
            13,
            MUTED,
            Typeface.NORMAL
    );

    pageContainer.addView(hint);
  }

  // =========================
  // DIALER PAGE
  // =========================

  void dialerPage() {

    addSpace(8);

    TextView heading = text(
            "Dialer",
            30,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(heading);

    addSpace(18);

    peerId = input("Enter User ID");

    peerId.setTextSize(22);
    peerId.setGravity(Gravity.CENTER);

    pageContainer.addView(
            peerId,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(62)
            )
    );

    addSpace(20);

    String[][] keys = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"*", "0", "#"}
    };

    for (String[] row : keys) {

      LinearLayout rowLayout =
              new LinearLayout(this);

      rowLayout.setGravity(Gravity.CENTER);

      for (String key : row) {

        Button keyButton = new Button(this);

        keyButton.setText(key);
        keyButton.setTextSize(22);
        keyButton.setTextColor(TEXT);
        keyButton.setAllCaps(false);

        keyButton.setBackground(
                rounded(CARD2, 100)
        );

        keyButton.setOnClickListener(
                v -> peerId.append(key)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(68),
                        1
                );

        params.setMargins(
                dp(5), dp(5),
                dp(5), dp(5)
        );

        rowLayout.addView(keyButton, params);
      }

      pageContainer.addView(rowLayout);
    }

    addSpace(10);

    LinearLayout controls =
            new LinearLayout(this);

    controls.setGravity(Gravity.CENTER);

    Button backspace =
            actionButton("⌫", CARD2);

    backspace.setOnClickListener(v -> {

      String s = peerId.getText().toString();

      if (!s.isEmpty()) {
        peerId.setText(
                s.substring(0, s.length() - 1)
        );
        peerId.setSelection(peerId.length());
      }
    });

    controls.addView(
            backspace,
            new LinearLayout.LayoutParams(
                    dp(85),
                    dp(58)
            )
    );

    addSpaceTo(controls, 10);

    Button callButton =
            actionButton("☎  Call", GREEN);

    callButton.setOnClickListener(v -> call());

    controls.addView(
            callButton,
            new LinearLayout.LayoutParams(
                    0,
                    dp(58),
                    1
            )
    );

    pageContainer.addView(controls);
  }

  // =========================
  // SETTINGS PAGE
  // =========================

  void settingsPage() {

    addSpace(6);

    TextView heading = text(
            "Settings",
            30,
            TEXT,
            Typeface.BOLD
    );

    pageContainer.addView(heading);

    addSpace(18);

    LinearLayout network = card();

    TextView title = text(
            "Server",
            18,
            TEXT,
            Typeface.BOLD
    );

    network.addView(title);

    addSpaceTo(network, 12);

    server = input(
            "Server URL"
    );
    server.setText(savedServerUrl);

    network.addView(
            server,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    pageContainer.addView(network);

    addSpace(14);

    LinearLayout account = card();

    TextView accountTitle = text(
            "Account",
            18,
            TEXT,
            Typeface.BOLD
    );

    account.addView(accountTitle);

    addSpaceTo(account, 12);

    userId = input(
            "Your User ID"
    );
    userId.setText(savedUserId);

    account.addView(
            userId,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    addSpaceTo(account, 12);

    Button connect =
            actionButton(
                    "Connect",
                    ACCENT
            );

    connect.setOnClickListener(v -> connect());

    account.addView(connect);

    pageContainer.addView(account);

    addSpace(18);

    TextView info = text(
            "VirtualPhone • Phase 3\nReal-time WebSocket calling",
            13,
            MUTED,
            Typeface.NORMAL
    );

    info.setGravity(Gravity.CENTER);

    pageContainer.addView(info);
  }

  // =========================
  // WEBSOCKET
  // =========================

  void connect() {

    if (server == null || userId == null) {
      showPage(5);
      return;
    }

    String serverUrl =
            server.getText().toString().trim();

    String uid =
            userId.getText().toString().trim();

    if (serverUrl.isEmpty()) {
      Toast.makeText(
              this,
              "Enter a server URL first",
              Toast.LENGTH_SHORT
      ).show();
      return;
    }

    if (uid.isEmpty()) {
      Toast.makeText(
              this,
              "Enter your User ID first",
              Toast.LENGTH_SHORT
      ).show();
      return;
    }

    savedServerUrl = serverUrl;
    savedUserId = uid;

    try {

      String wsUrl =
              serverUrl
                      .replace("http://", "ws://")
                      .replace("https://", "wss://");

      if (!wsUrl.endsWith("/ws")) {
        wsUrl += "/ws";
      }

      final URI endpoint = URI.create(wsUrl);

      ws = new WebSocketClient(endpoint) {

        @Override
        protected void onSetSSLParameters(SSLParameters sslParameters) {
          // Android 4.3 does not implement
          // SSLParameters.setEndpointIdentificationAlgorithm().
          // Do not call the library's default implementation on old Android.
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            super.onSetSSLParameters(sslParameters);
          }
        }

        @Override
        public void onOpen(ServerHandshake h) {

          // On older Android, hostname verification is performed manually here.
          // The platform trust manager still validates the certificate chain.
          if ("wss".equalsIgnoreCase(endpoint.getScheme())) {
            try {
              if (!(getSocket() instanceof SSLSocket)) {
                throw new SSLHandshakeException("Secure WebSocket socket unavailable");
              }

              SSLSession session = ((SSLSocket) getSocket()).getSession();
              boolean hostnameValid = HttpsURLConnection
                      .getDefaultHostnameVerifier()
                      .verify(endpoint.getHost(), session);

              if (!hostnameValid) {
                ui("TLS hostname verification failed");
                setConnected(false);
                close();
                return;
              }
            } catch (Exception e) {
              ui("TLS verification failed: " + e.getMessage());
              setConnected(false);
              close();
              return;
            }
          }

          try {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("userId", uid);
            send(auth.toString());
          } catch (Exception e) {
            ui("Could not authenticate: " + e.getMessage());
            setConnected(false);
            close();
            return;
          }

          ui("Connected");
          setConnected(true);
        }

        @Override
        public void onMessage(String s) {
          handle(s);
        }

        @Override
        public void onMessage(ByteBuffer x) {

          byte[] b =
                  new byte[x.remaining()];

          x.get(b);

          if (audio != null) {
            audio.play(b);
          }
        }

        @Override
        public void onClose(
                int c,
                String r,
                boolean remote
        ) {

          ui("Disconnected" + (r == null || r.isEmpty() ? "" : ": " + r));
          setConnected(false);
          stopAudio();
        }

        @Override
        public void onError(Exception e) {

          final String errorMessage = e.getClass().getSimpleName()
                  + (e.getMessage() == null ? "" : ": " + e.getMessage());
          ui("WebSocket error: " + errorMessage);

          runOnUiThread(() -> Toast.makeText(
                  MainActivity.this,
                  errorMessage,
                  Toast.LENGTH_LONG
          ).show());

          setConnected(false);
        }
      };

      ws.connect();

    } catch (Exception e) {

      ui(
              "Bad server URL: "
                      + e.getMessage()
      );

      setConnected(false);
    }
  }

  // =========================
  // INCOMING / CALL STATE
  // =========================

  void handle(String s) {

    try {

      JSONObject o =
              new JSONObject(s);

      String t =
              o.optString("type");

      if (t.equals("call_incoming")) {

        callId =
                o.optString("callId");

        String from =
                o.optString("from");

        runOnUiThread(() -> {

          new AlertDialog.Builder(this)

                  .setTitle("Incoming Call")

                  .setMessage(
                          "Incoming call from\n\n"
                                  + from
                  )

                  .setPositiveButton(
                          "Accept",
                          (d, w) -> {

                            send(
                                    "{\"type\":\"call_answer\",\"callId\":\""
                                            + callId
                                            + "\",\"from\":\""
                                            + from
                                            + "\",\"accept\":true}"
                            );

                            startAudio();
                          }
                  )

                  .setNegativeButton(
                          "Decline",
                          (d, w) -> {

                            send(
                                    "{\"type\":\"call_answer\",\"callId\":\""
                                            + callId
                                            + "\",\"from\":\""
                                            + from
                                            + "\",\"accept\":false}"
                            );
                          }
                  )

                  .show();
        });
      } else if (t.equals("message_receive")) {

        final String from = o.optString("from");
        final String body = o.optString("text");

        runOnUiThread(() -> {

          String me = "";

          if (userId != null) {
            me = userId.getText()
                    .toString()
                    .trim();
          }

          messages.add(
                  new String[]{
                          from,
                          me,
                          body
                  }
          );

          activeChatUser = from;
          if (messageRecipient != null) {
            messageRecipient.setText(from);
          }

          refreshMessages();

          if (currentPage != 2) {
            Toast.makeText(
                    MainActivity.this,
                    "New message from " + from,
                    Toast.LENGTH_LONG
            ).show();
          }
        });

      } else if (t.equals("message_error")) {

        final String message = o.optString("message", "Message could not be sent");
        runOnUiThread(() -> Toast.makeText(
                MainActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show());

      } else if (t.equals("call_state")) {

        String incomingCallId = o.optString("callId");
        if (!incomingCallId.isEmpty()) {
          callId = incomingCallId;
        }

        String state = o.optString("state");
        ui("Call: " + state);

        if (state.equals("active")) {
          startAudio();
        } else if (state.equals("ended") || state.equals("failed")) {
          stopAudio();
        }

        if (state.equals("failed")) {
          String reason = o.optString("reason", "Call failed");
          ui("Call failed: " + reason);
        }
      }

    } catch (Exception e) {

      e.printStackTrace();
    }
  }

  // =========================
  // CALL CONTROLS
  // =========================
  void sendTextMessage() {

    if (ws == null || !ws.isOpen()) {

      Toast.makeText(
              this,
              "Connect to the server first",
              Toast.LENGTH_SHORT
      ).show();

      return;
    }

    if (messageRecipient == null ||
            messageInput == null) {
      return;
    }

    String to =
            messageRecipient.getText()
                    .toString()
                    .trim();

    String body =
            messageInput.getText()
                    .toString()
                    .trim();

    if (to.isEmpty()) {

      Toast.makeText(
              this,
              "Enter a recipient User ID",
              Toast.LENGTH_SHORT
      ).show();

      return;
    }

    if (body.isEmpty()) {
      return;
    }

    String me =
            userId.getText()
                    .toString()
                    .trim();

    try {

      JSONObject o = new JSONObject();

      o.put("type", "message_send");
      o.put("to", to);
      o.put("text", body);

      send(o.toString());

      messages.add(
              new String[]{
                      me,
                      to,
                      body
              }
      );

      activeChatUser = to;

      messageInput.setText("");

      refreshMessages();

    } catch (Exception e) {

      Toast.makeText(
              this,
              "Could not send message",
              Toast.LENGTH_SHORT
      ).show();
    }
  }

  void refreshMessages() {

    if (messageList == null) {
      return;
    }

    messageList.removeAllViews();

    String me = "";

    if (userId != null) {
      me = userId.getText()
              .toString()
              .trim();
    }

    for (String[] msg : messages) {

      String from = msg[0];
      String to = msg[1];
      String body = msg[2];

      boolean belongsToChat =
              activeChatUser.isEmpty() ||
                      (
                              from.equals(me) &&
                                      to.equals(activeChatUser)
                      ) ||
                      (
                              from.equals(activeChatUser) &&
                                      to.equals(me)
                      );

      if (!belongsToChat) {
        continue;
      }

      boolean outgoing =
              from.equals(me);

      TextView bubble = text(
              (outgoing ? "You" : from)
                      + "\n"
                      + body,
              15,
              TEXT,
              Typeface.NORMAL
      );

      bubble.setPadding(
              dp(14),
              dp(10),
              dp(14),
              dp(10)
      );

      bubble.setBackground(
              rounded(
                      outgoing
                              ? ACCENT
                              : CARD2,
                      18
              )
      );

      LinearLayout.LayoutParams params =
              new LinearLayout.LayoutParams(
                      ViewGroup.LayoutParams.WRAP_CONTENT,
                      ViewGroup.LayoutParams.WRAP_CONTENT
              );

      params.setMargins(
              dp(2),
              dp(5),
              dp(2),
              dp(5)
      );

      if (outgoing) {
        params.gravity = Gravity.END;
      } else {
        params.gravity = Gravity.START;
      }

      messageList.addView(
              bubble,
              params
      );
    }
  }
  void call() {

    if (ws == null || !ws.isOpen()) {

      Toast.makeText(
              this,
              "Connect to the server first",
              Toast.LENGTH_SHORT
      ).show();

      return;
    }

    if (peerId == null) {
      return;
    }

    String peer =
            peerId.getText().toString().trim();

    if (peer.isEmpty()) {

      Toast.makeText(
              this,
              "Enter a User ID",
              Toast.LENGTH_SHORT
      ).show();

      return;
    }

    send(
            "{\"type\":\"call_start\",\"to\":\""
                    + peer
                    + "\"}"
    );

    ui("Calling " + peer);
  }

  void hang() {

    if (callId != null) {

      send(
              "{\"type\":\"call_end\",\"callId\":\""
                      + callId
                      + "\"}"
      );
    }

    stopAudio();
    ui("Call ended");
  }

  // =========================
  // AUDIO
  // =========================

  void startAudio() {

    if (audio != null) {
      return;
    }

    audio = new LegacyAudio(
            pcm -> {

              if (ws != null &&
                      ws.isOpen()) {

                ws.send(pcm);
              }
            }
    );

    audio.start();

    ui("Microphone active");
  }

  void stopAudio() {

    if (audio != null) {

      audio.stop();
      audio = null;
    }

    callId = null;
  }

  // =========================
  // UI STATUS
  // =========================

  void send(String s) {

    if (ws != null &&
            ws.isOpen()) {

      ws.send(s);
    }
  }

  void ui(final String s) {

    runOnUiThread(() -> {

      if (status != null) {
        status.setText(s);
      }

      if (callInfo != null) {

        if (callId != null) {
          callInfo.setText(s);
        } else if (
                s.toLowerCase()
                        .contains("ended")
        ) {
          callInfo.setText(
                  "No active call"
          );
        }
      }
    });
  }

  void setConnected(boolean connected) {

    runOnUiThread(() -> {

      if (connectionBadge == null) {
        return;
      }

      if (connected) {

        connectionBadge.setText(
                "● ONLINE"
        );

        connectionBadge.setTextColor(
                GREEN
        );

        connectionBadge.setBackground(
                rounded(
                        Color.rgb(20, 55, 40),
                        50
                )
        );

      } else {

        connectionBadge.setText(
                "OFFLINE"
        );

        connectionBadge.setTextColor(
                MUTED
        );

        connectionBadge.setBackground(
                rounded(
                        Color.rgb(40, 43, 55),
                        50
                )
        );
      }
    });
  }

  // =========================
  // UI HELPERS
  // =========================

  TextView text(
          String value,
          float size,
          int color,
          int typeface
  ) {

    TextView t =
            new TextView(this);

    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setTypeface(
            Typeface.DEFAULT,
            typeface
    );

    return t;
  }

  EditText input(String hint) {

    EditText e =
            new EditText(this);

    e.setHint(hint);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setTextSize(16);
    e.setSingleLine(true);
    e.setPadding(
            dp(16),
            0,
            dp(16),
            0
    );

    e.setBackground(
            rounded(CARD2, 18)
    );

    return e;
  }

  Button actionButton(
          String label,
          int color
  ) {

    Button b =
            new Button(this);

    b.setText(label);
    b.setTextColor(
            color == GREEN ||
                    color == RED ||
                    color == ACCENT
                    ? Color.WHITE
                    : TEXT
    );

    b.setTextSize(16);
    b.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
    );

    b.setAllCaps(false);

    b.setBackground(
            rounded(color, 20)
    );

    return b;
  }

  LinearLayout card() {

    LinearLayout l =
            new LinearLayout(this);

    l.setOrientation(
            LinearLayout.VERTICAL
    );

    l.setPadding(
            dp(18),
            dp(18),
            dp(18),
            dp(18)
    );

    l.setBackground(
            rounded(CARD, 24)
    );

    // View elevation was added in Android 5.0 (API 21).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      l.setElevation(dp(2));
    }

    return l;
  }

  GradientDrawable rounded(
          int color,
          int radius
  ) {

    GradientDrawable g =
            new GradientDrawable();

    g.setColor(color);
    g.setCornerRadius(
            dp(radius)
    );

    return g;
  }

  void addSpace(int height) {

    Space s =
            new Space(this);

    pageContainer.addView(
            s,
            new LinearLayout.LayoutParams(
                    1,
                    dp(height)
            )
    );
  }

  void addSpaceTo(
          LinearLayout parent,
          int height
  ) {

    Space s =
            new Space(this);

    parent.addView(
            s,
            new LinearLayout.LayoutParams(
                    1,
                    dp(height)
            )
    );
  }

  int dp(int value) {

    return (int) (
            value *
                    getResources()
                            .getDisplayMetrics()
                            .density
    );
  }

  // =========================
  // CLEANUP
  // =========================

  @Override
  protected void onDestroy() {

    stopAudio();

    if (ws != null) {
      ws.close();
    }

    super.onDestroy();
  }
}
