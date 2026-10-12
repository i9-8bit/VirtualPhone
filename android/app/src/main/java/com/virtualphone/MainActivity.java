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
import android.view.View;
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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

public class MainActivity extends Activity {

  // =========================
  // BACKEND / CALL VARIABLES
  // =========================

  private static final String DEFAULT_SERVER_URL =
          "https://clan-rounds-bug-genealogy.trycloudflare.com";
  private static final String PREFS_NAME = "virtualphone_preferences";

  EditText server, userId, peerId;
  String savedServerUrl = DEFAULT_SERVER_URL;
  String savedUserId = "";
  Button[] navButtons;
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
    if (apiLevel() >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(BG);
      getWindow().setNavigationBarColor(BG);
    }

    android.content.SharedPreferences prefs =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    savedServerUrl = prefs.getString("serverUrl", DEFAULT_SERVER_URL);
    if (savedServerUrl == null || savedServerUrl.trim().length() == 0) {
      savedServerUrl = DEFAULT_SERVER_URL;
    }
    savedUserId = prefs.getString("userId", "");

    buildApp();

    if (apiLevel() >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

      requestPermissions(
              new String[]{Manifest.permission.RECORD_AUDIO},
              7
      );
    }
  }

  /*
   * SDK_INT was added in Android API 4. Reflectively read it so the app can
   * start on API 1–3 too; fall back to their old release strings on those OSs.
   */
  private int apiLevel() {
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

  private static final int REQUEST_LOCAL_NETWORK = 3701;

  @Override
  public void onRequestPermissionsResult(
          int requestCode, String[] permissions, int[] grantResults) {
    // Avoid invoking API-23-only superclass methods on Android 4.x.
    if (requestCode == REQUEST_LOCAL_NETWORK) {
      if (grantResults.length > 0
              && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        connect();
      } else {
        Toast.makeText(this,
                "Allow Local Network access to connect to a server on your Wi-Fi.",
                Toast.LENGTH_LONG).show();
      }
    }
  }

  private boolean isLikelyLocalNetworkHost(String host) {
    if (host == null) return false;
    String h = host.trim().toLowerCase(java.util.Locale.US);
    if (h.startsWith("[") && h.endsWith("]")) {
      h = h.substring(1, h.length() - 1);
    }
    if ("localhost".equals(h) || h.endsWith(".local")
            || h.endsWith(".lan") || h.endsWith(".home")) return true;
    if ("::1".equals(h) || h.startsWith("fe80:")
            || h.startsWith("fc") || h.startsWith("fd")) return true;
    String[] octets = h.split("\\.");
    if (octets.length == 4) {
      try {
        int x = Integer.parseInt(octets[0]);
        int y = Integer.parseInt(octets[1]);
        int z = Integer.parseInt(octets[2]);
        int w = Integer.parseInt(octets[3]);
        if (x < 0 || x > 255 || y < 0 || y > 255
                || z < 0 || z > 255 || w < 0 || w > 255) return false;
        return x == 10 || x == 127 || (x == 169 && y == 254)
                || (x == 172 && y >= 16 && y <= 31)
                || (x == 192 && y == 168)
                || (x == 100 && y >= 64 && y <= 127);
      } catch (NumberFormatException ignored) {
        return false;
      }
    }
    // Avoid DNS lookups on the UI thread. Some unusual names that resolve to
    // a private IP may still need a later refinement of this check.
    return h.indexOf('.') < 0 && h.indexOf(':') < 0;
  }

  // =========================
  // BUILD ENTIRE UI
  // =========================

  void buildApp() {

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundDrawable(new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(13, 19, 33), BG}
    ));

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

    connectionBadge.setBackgroundDrawable(
            rounded(Color.rgb(40, 43, 55), 50)
    );

    top.addView(connectionBadge);

    root.addView(top);

    android.view.View accentRule = new android.view.View(this);
    accentRule.setBackgroundDrawable(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ACCENT, PURPLE, GREEN}
    ));
    root.addView(accentRule, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

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
            "Chat",
            "People",
            "Dial",
            "Setup"
    };
    navButtons = new Button[navNames.length];

    for (int i = 0; i < navNames.length; i++) {
      final int page = i;

      Button b = new Button(this);
      b.setText(navNames[i]);
      b.setTextColor(MUTED);
      b.setTextSize(10);
      setAllCapsCompat(b, false);
      b.setPadding(dp(2), 0, dp(2), 0);
      b.setBackgroundDrawable(rounded(Color.rgb(17, 20, 30), 15));
      navButtons[i] = b;

      b.setOnClickListener(v -> showPage(page));

      LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
              0, dp(50), 1
      );
      navParams.setMargins(dp(2), 0, dp(2), 0);
      nav.addView(b, navParams);
    }

    root.addView(nav);

    // Keep content clear of status/navigation bars and display cutouts on modern phones.
    // Android 15+ enforces edge-to-edge for the corresponding modern-target APKs.
    // Older releases use the normal decor insets already; don't add them twice.
    if (apiLevel() >= 35) {
      final int originalLeft = root.getPaddingLeft();
      final int originalTop = root.getPaddingTop();
      final int originalRight = root.getPaddingRight();
      final int originalBottom = root.getPaddingBottom();
      root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
        @Override
        public android.view.WindowInsets onApplyWindowInsets(
                View view, android.view.WindowInsets insets) {
          view.setPadding(
                  originalLeft + insets.getSystemWindowInsetLeft(),
                  originalTop + insets.getSystemWindowInsetTop(),
                  originalRight + insets.getSystemWindowInsetRight(),
                  originalBottom + insets.getSystemWindowInsetBottom());
          return insets;
        }
      });
      root.requestApplyInsets();
    }

    setContentView(root);

    showPage(0);
  }

  private void updateNavigation() {
    if (navButtons == null) return;
    for (int i = 0; i < navButtons.length; i++) {
      boolean selected = i == currentPage;
      navButtons[i].setTextColor(selected ? Color.WHITE : MUTED);
      navButtons[i].setBackgroundDrawable(rounded(
              selected ? Color.rgb(31, 52, 80) : Color.rgb(17, 20, 30),
              15
      ));
    }
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
    updateNavigation();
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString("serverUrl", savedServerUrl)
            .putString("userId", savedUserId)
            .commit();
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

    LinearLayout hero = card();
    GradientDrawable heroBackground = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(22, 46, 75), Color.rgb(40, 31, 71)}
    );
    heroBackground.setCornerRadius(dp(24));
    hero.setBackgroundDrawable(heroBackground);

    TextView heroTag = text(
            "VOICE  •  CHAT  •  CONNECT",
            11,
            Color.rgb(151, 210, 255),
            Typeface.BOLD
    );
    hero.addView(heroTag);
    addSpaceTo(hero, 10);

    TextView welcome = text(
            "Your phone,\nconnected.",
            29,
            TEXT,
            Typeface.BOLD
    );
    hero.addView(welcome);
    addSpaceTo(hero, 8);

    TextView subtitle = text(
            "Make calls and send messages through your VirtualPhone server.",
            14,
            Color.rgb(210, 217, 233),
            Typeface.NORMAL
    );
    hero.addView(subtitle);
    pageContainer.addView(hero);

    addSpace(14);

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

    if (activeChatUser.length() != 0) {
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
            activeChatUser.length() == 0
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
    avatar.setBackgroundDrawable(
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
        setAllCapsCompat(keyButton, false);

        keyButton.setBackgroundDrawable(
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

      if (s.length() != 0) {
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
    server.setText(savedServerUrl == null || savedServerUrl.trim().length() == 0
            ? DEFAULT_SERVER_URL : savedServerUrl);

    network.addView(
            server,
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            )
    );

    addSpaceTo(network, 8);
    TextView serverHint = text(
            apiLevel() <= 13
                    ? "Android 1–3: legacy TLS mode requires a compatible local proxy. Wolfius's current upstream build disables upstream certificate verification; use test IDs only."
                    : "Quick Tunnel URL • temporary; it may change when the tunnel restarts.",
            12,
            MUTED,
            Typeface.NORMAL
    );
    network.addView(serverHint);

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

    if (serverUrl.length() == 0) {
      Toast.makeText(
              this,
              "Enter a server URL first",
              Toast.LENGTH_SHORT
      ).show();
      return;
    }

    if (uid.length() == 0) {
      Toast.makeText(
              this,
              "Enter your User ID first",
              Toast.LENGTH_SHORT
      ).show();
      return;
    }

    savedServerUrl = serverUrl;
    savedUserId = uid;
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString("serverUrl", savedServerUrl)
            .putString("userId", savedUserId)
            .commit();

    try {

      String wsUrl =
              serverUrl
                      .replace("http://", "ws://")
                      .replace("https://", "wss://");

      if (!wsUrl.endsWith("/ws")) {
        wsUrl += "/ws";
      }

      final URI endpoint = URI.create(wsUrl);

      // Android 17 requires a runtime permission for direct LAN access.
      if (apiLevel() >= 37
              && isLikelyLocalNetworkHost(endpoint.getHost())
              && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK")
                      != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
                new String[]{"android.permission.ACCESS_LOCAL_NETWORK"},
                REQUEST_LOCAL_NETWORK
        );
        return;
      }

      ws = new WebSocketClient(endpoint) {

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

          ui("Disconnected" + (r == null || r.length() == 0 ? "" : ": " + r));
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

      // Connecting the raw TCP socket is done off the UI thread. The custom
      // SSLSocket is host-aware (SNI) and is installed before WebSocketClient
      // starts, so the library does not create a second TLS wrapper.
      final WebSocketClient connectingClient = ws;
      if ("wss".equalsIgnoreCase(endpoint.getScheme())) {
        Thread tlsConnect = new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              int tlsPort = endpoint.getPort() > 0 ? endpoint.getPort() : 443;
              connectingClient.setSocket(
                      LegacyTlsSocketFactory.createSocket(endpoint.getHost(), tlsPort));
              connectingClient.connect();
            } catch (Exception e) {
              setConnected(false);
              ui("Secure connection failed: " + e.getMessage());
              try { connectingClient.close(); } catch (Exception ignored) {}
            }
          }
        }, "VirtualPhone-TLS-connect");
        tlsConnect.start();
      } else {
        connectingClient.connect();
      }

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
        if (incomingCallId.length() != 0) {
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

    if (to.length() == 0) {

      Toast.makeText(
              this,
              "Enter a recipient User ID",
              Toast.LENGTH_SHORT
      ).show();

      return;
    }

    if (body.length() == 0) {
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
              activeChatUser.length() == 0 ||
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

      bubble.setBackgroundDrawable(
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

    if (peer.length() == 0) {

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

        connectionBadge.setBackgroundDrawable(
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

        connectionBadge.setBackgroundDrawable(
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

    e.setBackgroundDrawable(
            rounded(CARD2, 18)
    );

    return e;
  }

  // TextView.setAllCaps was added after Android 1–3.
  // Keep old Android builds safe; older Buttons may use their theme's casing.
  private void setAllCapsCompat(TextView view, boolean allCaps) {
    if (apiLevel() >= 14) {
      view.setAllCaps(allCaps);
    }
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

    setAllCapsCompat(b, false);
    b.setMinimumHeight(dp(50));
    b.setPadding(dp(12), dp(4), dp(12), dp(4));

    b.setBackgroundDrawable(
            rounded(color, 20)
    );

    if (apiLevel() >= Build.VERSION_CODES.LOLLIPOP) {
      b.setElevation(dp(1));
    }

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

    GradientDrawable cardBackground = rounded(CARD, 24);
    cardBackground.setStroke(dp(1), Color.rgb(39, 48, 67));
    l.setBackgroundDrawable(cardBackground);

    // View elevation was added in Android 5.0 (API 21).
    if (apiLevel() >= Build.VERSION_CODES.LOLLIPOP) {
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
