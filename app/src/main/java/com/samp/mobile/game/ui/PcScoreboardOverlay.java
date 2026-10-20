package com.samp.mobile.game.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Lightweight SA-MP-style scoreboard rendered above the game surface.
 *
 * Native client callbacks feed this view through SAMP.showTab(), hideTab(),
 * setTab(), and clearTab(). This class intentionally contains presentation
 * only; it does not invent player data or change the network protocol.
 */
public final class PcScoreboardOverlay {
    private static final int PANEL_COLOR = Color.argb(232, 12, 16, 22);
    private static final int HEADER_COLOR = Color.argb(245, 27, 38, 50);
    private static final int ACCENT_COLOR = Color.rgb(91, 166, 220);
    private static final int TEXT_COLOR = Color.rgb(238, 243, 248);
    private static final int MUTED_COLOR = Color.rgb(166, 183, 199);

    private final Activity activity;
    private final FrameLayout root;
    private final LinearLayout panel;
    private final LinearLayout playerRows;
    private final TextView playerCount;
    private final SparseArray<PlayerRow> players = new SparseArray<>();

    public PcScoreboardOverlay(Activity activity) {
        this.activity = activity;

        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);
        root.setClickable(false);
        root.setFocusable(false);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(background(PANEL_COLOR, ACCENT_COLOR, dp(4)));
        panel.setClickable(false);
        panel.setFocusable(false);

        int screenWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density);
        int panelWidth = Math.max(dp(280), Math.min(dp(720), dp(screenWidth - 24)));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panelParams.topMargin = dp(18);
        root.addView(panel, panelParams);

        LinearLayout titleBar = new LinearLayout(activity);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(dp(10), dp(4), dp(10), dp(4));
        titleBar.setBackground(background(HEADER_COLOR, Color.TRANSPARENT, dp(2)));

        TextView title = new TextView(activity);
        title.setText("PLAYER LIST");
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.04f);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1f));

        playerCount = new TextView(activity);
        playerCount.setText("PLAYERS: 0");
        playerCount.setTextColor(ACCENT_COLOR);
        playerCount.setTextSize(11);
        playerCount.setTypeface(Typeface.DEFAULT_BOLD);
        playerCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleBar.addView(playerCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        panel.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout columnHeaders = new LinearLayout(activity);
        columnHeaders.setOrientation(LinearLayout.HORIZONTAL);
        columnHeaders.setGravity(Gravity.CENTER_VERTICAL);
        columnHeaders.setPadding(dp(8), 0, dp(8), 0);
        columnHeaders.setBackgroundColor(Color.argb(190, 35, 49, 64));
        addCell(columnHeaders, "ID", 0.65f, Gravity.START, true);
        addCell(columnHeaders, "PLAYER", 3.8f, Gravity.START, true);
        addCell(columnHeaders, "SCORE", 1.0f, Gravity.END, true);
        addCell(columnHeaders, "PING", 0.9f, Gravity.END, true);
        panel.addView(columnHeaders, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(29)));

        playerRows = new LinearLayout(activity);
        playerRows.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setClipToPadding(false);
        scrollView.addView(playerRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int screenHeightDp = (int) (activity.getResources().getDisplayMetrics().heightPixels
                / activity.getResources().getDisplayMetrics().density);
        int scrollHeightDp = Math.max(120, Math.min(420, (int) (screenHeightDp * 0.58f) - 78));
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(scrollHeightDp)));

        TextView footer = new TextView(activity);
        footer.setText("Hold TAB to view players");
        footer.setTextColor(MUTED_COLOR);
        footer.setTextSize(10);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(6), dp(6), dp(6), dp(2));
        panel.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        activity.addContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void show() {
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        panel.bringToFront();
        updatePlayerCount();
    }

    public void hide() {
        root.setVisibility(View.GONE);
    }

    public boolean isVisible() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void setPlayer(int id, String name, int score, int ping) {
        PlayerRow row = players.get(id);
        if (row == null) {
            row = createPlayerRow(id);
            players.put(id, row);
            playerRows.addView(row.container, players.indexOfKey(id));
            updateRowStripes();
        }

        String safeName = name == null ? "" : name;
        row.name.setText(safeName);
        row.score.setText(String.valueOf(score));
        row.ping.setText(String.valueOf(ping));
        row.container.setContentDescription("Player " + id + ", " + safeName
                + ", score " + score + ", ping " + ping);
        updatePlayerCount();
    }

    public void clearPlayers() {
        players.clear();
        playerRows.removeAllViews();
        updatePlayerCount();
    }

    private PlayerRow createPlayerRow(int id) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        row.setMinimumHeight(dp(29));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setClickable(false);
        row.setFocusable(false);

        TextView idView = addCell(row, String.valueOf(id), 0.65f, Gravity.START, false);
        TextView nameView = addCell(row, "", 3.8f, Gravity.START, false);
        TextView scoreView = addCell(row, "0", 1.0f, Gravity.END, false);
        TextView pingView = addCell(row, "0", 0.9f, Gravity.END, false);
        return new PlayerRow(row, idView, nameView, scoreView, pingView);
    }

    private TextView addCell(LinearLayout parent, String value, float weight,
                             int gravity, boolean heading) {
        TextView cell = new TextView(activity);
        cell.setText(value);
        cell.setTextColor(heading ? MUTED_COLOR : TEXT_COLOR);
        cell.setTextSize(heading ? 10 : 12);
        cell.setTypeface(heading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        cell.setGravity(gravity | Gravity.CENTER_VERTICAL);
        cell.setSingleLine(true);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setPadding(dp(3), 0, dp(3), 0);
        parent.addView(cell, new LinearLayout.LayoutParams(0, dp(29), weight));
        return cell;
    }

    private void updateRowStripes() {
        for (int index = 0; index < players.size(); index++) {
            PlayerRow row = players.valueAt(index);
            row.container.setBackgroundColor(index % 2 == 0
                    ? Color.argb(25, 255, 255, 255)
                    : Color.TRANSPARENT);
        }
    }

    private void updatePlayerCount() {
        playerCount.setText("PLAYERS: " + players.size());
    }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class PlayerRow {
        final LinearLayout container;
        final TextView id;
        final TextView name;
        final TextView score;
        final TextView ping;

        PlayerRow(LinearLayout container, TextView id, TextView name,
                  TextView score, TextView ping) {
            this.container = container;
            this.id = id;
            this.name = name;
            this.score = score;
            this.ping = ping;
        }
    }
}
