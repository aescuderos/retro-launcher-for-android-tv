package com.gamefront;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.parseColor("#101418");
    private static final int PANEL = Color.parseColor("#181E26");
    private static final int ACCENT = Color.parseColor("#2F7FD1");
    private static final String RA_PKG = "com.retroarch";
    private static final String RA_ACT = "com.retroarch.browser.retroactivity.RetroActivityFuture";
    private static final String RA_CFG = "/sdcard/Android/data/com.retroarch/files/retroarch.cfg";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private File base;
    private JSONArray systems;
    private int current = -1;
    private JSONArray games = new JSONArray();
    private ListView sysList;
    private GridView grid;
    private TextView header;
    private SharedPreferences prefs;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("front", MODE_PRIVATE);
        File[] dirs = getExternalFilesDirs(null);
        for (File d : dirs) {
            if (d != null && new File(d, "library.json").exists()) { base = d; break; }
        }
        if (base == null) {
            TextView t = new TextView(this);
            t.setText("No se encuentra library.json en:\n" + (dirs.length > 0 && dirs[0] != null ? dirs[0] : "?"));
            t.setTextSize(22);
            t.setTextColor(Color.WHITE);
            t.setBackgroundColor(BG);
            t.setPadding(60, 60, 60, 60);
            setContentView(t);
            return;
        }
        try {
            systems = new JSONObject(readFile(new File(base, "library.json"))).getJSONArray("systems");
        } catch (Exception e) {
            Toast.makeText(this, "Error leyendo library.json: " + e, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildUi();
        int last = Math.max(0, Math.min(prefs.getInt("system", 0), systems.length() - 1));
        showSystem(last);
        sysList.setSelection(last);
        sysList.requestFocus();
    }

    private void buildUi() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int listW = (int) (screenW * 0.21f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        header = new TextView(this);
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        header.setPadding((int) (16 * dp), (int) (10 * dp), 0, (int) (4 * dp));
        left.addView(header, new LinearLayout.LayoutParams(-1, -2));

        int cols = 5;
        int gap = (int) (6 * dp);
        int pad = (int) (10 * dp);
        grid = new GridView(this);
        grid.setNumColumns(cols);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setPadding(pad, (int) (4 * dp), pad, pad);
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        int cellW = (screenW - listW - 2 * pad - gap * (cols - 1)) / cols;
        grid.setAdapter(new GameAdapter(cellW));
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { launch(pos); }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                grid.setSelector(new ColorDrawable(has ? ACCENT : 0));
                if (has && resetPending) {
                    resetPending = false;
                    grid.setSelection(0);
                    grid.post(new Runnable() { @Override public void run() { grid.setSelection(0); grid.smoothScrollToPosition(0); } });
                }
            }
        });
        left.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        sysList = new ListView(this);
        sysList.setBackgroundColor(PANEL);
        sysList.setDivider(null);
        sysList.setVerticalScrollBarEnabled(false);
        sysList.setSelector(new ColorDrawable(ACCENT));
        sysList.setPadding(0, pad, 0, pad);
        sysList.setClipToPadding(false);
        sysList.setAdapter(new SystemAdapter());
        sysList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { showSystem(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        sysList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { enterGrid(); }
        });
        root.addView(sysList, new LinearLayout.LayoutParams(listW, -1));
        setContentView(root);
    }

    private void showSystem(int idx) {
        if (idx == current || idx < 0 || idx >= systems.length()) return;
        current = idx;
        prefs.edit().putInt("system", idx).apply();
        try {
            JSONObject s = systems.getJSONObject(idx);
            games = s.getJSONArray("games");
            header.setText(s.getString("name") + "  ·  " + games.length() + " juegos");
        } catch (Exception e) {
            games = new JSONArray();
        }
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
        grid.setSelection(0);
        resetPending = true;
        grid.setSelector(new ColorDrawable(grid.hasFocus() ? ACCENT : 0));
        grid.post(new Runnable() { @Override public void run() { grid.setSelection(0); grid.smoothScrollToPosition(0); } });
        ((BaseAdapter) sysList.getAdapter()).notifyDataSetChanged();
    }

    private boolean resetPending;

    private void enterGrid() {
        resetPending = false;
        grid.requestFocus();
        grid.setSelection(0);
        for (int d : new int[]{30, 120, 300}) grid.postDelayed(new Runnable() { @Override public void run() { grid.setSelection(0); } }, d);
    }

    private void launch(int pos) {
        try {
            JSONArray g = games.getJSONArray(pos);
            Intent i = new Intent();
            i.setComponent(new ComponentName(RA_PKG, RA_ACT));
            i.putExtra("ROM", g.getString(1));
            i.putExtra("LIBRETRO", systems.getJSONObject(current).getString("core"));
            i.putExtra("CONFIGFILE", RA_CFG);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir RetroArch: " + e, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (grid == null) return super.dispatchKeyEvent(e);
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT && sysList.hasFocus() && grid.getCount() > 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) enterGrid();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BUTTON_A) {
            return super.dispatchKeyEvent(new KeyEvent(e.getAction(), KeyEvent.KEYCODE_DPAD_CENTER));
        }
        if (k == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.getAction() == KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        if ((k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_BUTTON_R1) && e.getAction() == KeyEvent.ACTION_DOWN) {
            int n = systems.length();
            sysList.setSelection((current + (k == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : n - 1)) % n);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (grid != null && grid.hasFocus()) sysList.requestFocus(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    private static String readFile(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private class SystemAdapter extends BaseAdapter {
        @Override public int getCount() { return systems.length(); }
        @Override public Object getItem(int p) { return null; }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            TextView t = (TextView) v;
            if (t == null) {
                t = new TextView(MainActivity.this);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setSingleLine(true);
                t.setPadding((int) (20 * dp), 0, (int) (12 * dp), 0);
                t.setLayoutParams(new AbsListView.LayoutParams(-1, (int) (46 * dp)));
            }
            try { t.setText(systems.getJSONObject(p).getString("name")); } catch (Exception e) { t.setText("?"); }
            boolean sel = p == current;
            t.setTextColor(sel ? Color.WHITE : Color.parseColor("#A0A8B4"));
            t.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            return t;
        }
    }

    private class GameAdapter extends BaseAdapter {
        private final int cellW;
        GameAdapter(int w) { cellW = w; }
        @Override public int getCount() { return games.length(); }
        @Override public Object getItem(int p) { return null; }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            Cell c = (Cell) v;
            if (c == null) c = new Cell(cellW);
            try {
                JSONArray g = games.getJSONArray(p);
                c.bind(g.getString(0), g.getString(2));
            } catch (Exception e) {
                c.bind("?", "");
            }
            return c;
        }
    }

    private class Cell extends LinearLayout {
        private final ImageView img;
        private final TextView title;
        private String tag = "";

        Cell(int w) {
            super(MainActivity.this);
            setOrientation(VERTICAL);
            int pad = (int) (6 * dp);
            setPadding(pad, pad, pad, pad);
            img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(img, new LayoutParams(-1, (int) (w * 0.9f)));
            title = new TextView(MainActivity.this);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            title.setMinLines(2);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER);
            addView(title, new LayoutParams(-1, -2));
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        }

        void bind(String name, String rel) {
            title.setText(name);
            tag = rel;
            img.setImageDrawable(null);
            img.setBackgroundColor(Color.parseColor("#1E252E"));
            if (rel.isEmpty()) return;
            Bitmap bm = cache.get(rel);
            if (bm != null) { img.setImageBitmap(bm); img.setBackgroundColor(0); return; }
            final String key = rel;
            pool.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap b = BitmapFactory.decodeFile(new File(base, "thumbs/" + key).getPath());
                    if (b == null) return;
                    cache.put(key, b);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (key.equals(tag)) { img.setImageBitmap(b); img.setBackgroundColor(0); }
                        }
                    });
                }
            });
        }
    }
}
