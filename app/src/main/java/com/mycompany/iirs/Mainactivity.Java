package com.mycompany.iirs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/** IIRS Mobile - READ-ONLY viewer. Login, search, view. No add/edit/delete anywhere. */
public class MainActivity extends Activity {

    static final int BLUE = 0xFF0018F9, INK = 0xFF1A1A2E, MUTED = 0xFF6B6B8D, LINE = 0xFFE3E7FF;
    static final int PER_PAGE = 8;
    static final String[] CAT_LABELS = {"General", "Property", "Person", "Cellphone", "Vehicle", "Incident"};
    static final String[] CAT_KEYS   = {"all", "properties", "persons", "contacts", "vehicles", "incidents"};

    static final HashMap<String, String> LBL = new HashMap<String, String>();
    static final HashMap<String, String> SEC = new HashMap<String, String>();
    static {
        LBL.put("str_no", "Street No"); LBL.put("erf", "ERF"); LBL.put("gv_value", "GV Value");
        LBL.put("cas_number", "CAS Number"); LBL.put("dob", "Date of Birth"); LBL.put("id_number", "ID Number");
        LBL.put("vin", "VIN"); LBL.put("town_allotment", "Town / Allotment"); LBL.put("extent_ha", "Extent (ha)");
        LBL.put("new_category", "Category"); LBL.put("engine_no", "Engine No");
        SEC.put("contacts", "CONTACTS"); SEC.put("properties", "LINKED PROPERTIES");
        SEC.put("vehicles", "VEHICLES"); SEC.put("incidents", "INCIDENTS");
        SEC.put("links", "LINKED PERSONS"); SEC.put("history", "HISTORY");
        SEC.put("prop_contacts", "PROPERTY CONTACTS"); SEC.put("linked", "LINKED RECORDS");
    }

    static final String[] SYS = {"created_at", "updated_at", "import_batch", "source_file"};
    static final String[] ORD_PERSON = {"first_name", "last_name", "id_number", "dob", "gender", "marital_status", "address", "city", "notes"};
    static final String[] ORD_PROPERTY = {"registered_owner", "co_owner", "str_no", "physical_address", "suburb", "town_allotment", "erf", "portion", "section", "unit", "extent_ha", "new_category", "gv_value", "notes"};
    static final String[] ORD_VEHICLE = {"plate", "make", "model", "color", "year", "vin", "engine_no", "owner_name", "notes"};
    static final String[] ORD_INCIDENT = {"cas_number", "incident_type", "incident_date", "location", "description", "status", "priority", "notes"};
    static final String[] ORD_CHILD = {"type", "value", "person_name", "role", "plate", "make", "model", "color", "str_no", "physical_address", "registered_owner", "cas_number", "incident_type", "incident_date", "record_type", "record", "changed_at", "change_type", "note", "notes", "status"};

    interface Job  { Object run() throws Exception; }
    interface Done { void ok(Object r) throws Exception; }

    SharedPreferences prefs;
    TextView subtitle, tvUser, tvPage;
    EditText etUrl, etUser, etPass, etTerm;
    Spinner spCat;
    ScrollView panelLogin, panelDetail, resultsScroll;
    LinearLayout panelSearch, resultsBox, detailBox, pager;
    Button btnPrev, btnNext;
    ProgressBar loading;
    View current;

    ArrayList<JSONObject> results = new ArrayList<JSONObject>();
    int page = 0, serverPage = 1;
    boolean hasMore = false, searched = false;
    String curQ = "", curCat = "all";

    // ------------------------------------------------------------------ setup
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("iirs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setBackgroundColor(BLUE);
        TextView title = tv("IIRS", 24, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(title);
        subtitle = tv("Read-only access", 12, 0xFFAABBFF, false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(subtitle);
        root.addView(head, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView conf = tv("CONFIDENTIAL  \u2022  AUTHORISED USE ONLY", 11, BLUE, true);
        conf.setGravity(Gravity.CENTER);
        conf.setBackgroundColor(0xFFE8ECFF);
        conf.setPadding(0, dp(6), 0, dp(6));
        root.addView(conf, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildLogin(); buildSearch(); buildDetail();
        body.addView(panelLogin); body.addView(panelSearch); body.addView(panelDetail);
        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        body.addView(loading, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));

        show(panelLogin);
        setContentView(root);
    }

    @Override public void onBackPressed() {
        if (current == panelDetail) show(panelSearch); else super.onBackPressed();
    }

    // ----------------------------------------------------------------- login
    void buildLogin() {
        panelLogin = new ScrollView(this);
        LinearLayout c = col(28);
        panelLogin.addView(c);
        c.addView(tv("Sign In", 22, INK, true), lp(-2, -2, 24, 6));
        c.addView(tv("Enter your credentials to continue", 13, MUTED, false), lp(-2, -2, 0, 24));
        c.addView(label("SERVER"), lp(-1, -2, 0, 6));
        etUrl = field("http://127.0.0.1:5000", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setText(prefs.getString("url", "http://127.0.0.1:5000"));
        c.addView(etUrl, lp(-1, dp(52), 0, 16));
        c.addView(label("USERNAME"), lp(-1, -2, 0, 6));
        etUser = field("Enter username", InputType.TYPE_CLASS_TEXT);
        c.addView(etUser, lp(-1, dp(52), 0, 16));
        c.addView(label("PASSWORD"), lp(-1, -2, 0, 6));
        etPass = field("Enter password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        c.addView(etPass, lp(-1, dp(52), 0, 24));
        Button go = btn("Sign In");
        c.addView(go, lp(-1, dp(52), 0, 0));
        go.setOnClickListener(v -> login());
    }

    void login() {
        String url = etUrl.getText().toString().trim();
        final String u = etUser.getText().toString().trim();
        final String p = etPass.getText().toString();
        if (url.isEmpty() || u.isEmpty() || p.isEmpty()) { toast("Server, username and password required"); return; }
        if (!url.startsWith("http")) url = "http://" + url;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        final String fUrl = url;
        Api.base = fUrl;
        bg(() -> {
            JSONObject b = new JSONObject();
            b.put("username", u);
            b.put("password", p);
            return new JSONObject(Api.post("/api/login", b.toString()));
        }, r -> {
            prefs.edit().putString("url", fUrl).apply();
            etPass.setText("");
            tvUser.setText("Signed in as " + u);
            show(panelSearch);
        });
    }

    void logout() {
        new Thread(() -> { try { Api.post("/api/logout", "{}"); } catch (Exception ignored) {} }).start();
        results.clear(); searched = false; page = 0;
        render();
        show(panelLogin);
    }

    // ---------------------------------------------------------------- search
    void buildSearch() {
        panelSearch = col(16);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        tvUser = tv("", 13, INK, true);
        top.addView(tvUser, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView out = tv("Sign out", 13, BLUE, true);
        out.setPadding(dp(8), dp(8), 0, dp(8));
        out.setOnClickListener(v -> logout());
        top.addView(out);
        panelSearch.addView(top, lp(-1, -2, 0, 8));

        panelSearch.addView(label("CATEGORY"), lp(-1, -2, 0, 6));
        LinearLayout sbox = new LinearLayout(this);
        sbox.setBackgroundResource(R.drawable.input_field);
        spCat = new Spinner(this);
        spCat.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, CAT_LABELS));
        sbox.addView(spCat, new LinearLayout.LayoutParams(-1, dp(52)));
        panelSearch.addView(sbox, lp(-1, -2, 0, 12));

        panelSearch.addView(label("SEARCH"), lp(-1, -2, 0, 6));
        etTerm = field("Name, address, plate, number\u2026", InputType.TYPE_CLASS_TEXT);
        etTerm.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etTerm.setOnEditorActionListener((v, a, e) -> { search(); return true; });
        panelSearch.addView(etTerm, lp(-1, dp(52), 0, 12));

        Button go = btn("Search");
        go.setOnClickListener(v -> search());
        panelSearch.addView(go, lp(-1, dp(52), 0, 12));

        resultsScroll = new ScrollView(this);
        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsScroll.addView(resultsBox);
        panelSearch.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        pager = new LinearLayout(this);
        pager.setGravity(Gravity.CENTER_VERTICAL);
        btnPrev = btn("\u2039 Prev");
        btnNext = btn("Next \u203A");
        tvPage = tv("", 13, INK, true);
        tvPage.setGravity(Gravity.CENTER);
        pager.addView(btnPrev, new LinearLayout.LayoutParams(dp(100), dp(44)));
        pager.addView(tvPage, new LinearLayout.LayoutParams(0, -2, 1f));
        pager.addView(btnNext, new LinearLayout.LayoutParams(dp(100), dp(44)));
        pager.setVisibility(View.GONE);
        panelSearch.addView(pager, lp(-1, -2, 8, 0));
        btnPrev.setOnClickListener(v -> { if (page > 0) { page--; render(); } });
        btnNext.setOnClickListener(v -> {
            if ((page + 1) * PER_PAGE < results.size()) { page++; render(); }
            else if (hasMore) { serverPage++; fetch(true); }
        });
        render();
    }

    void search() {
        String q = etTerm.getText().toString().trim();
        if (q.length() < 2) { toast("Enter at least 2 characters"); return; }
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(etTerm.getWindowToken(), 0);
        curQ = q;
        curCat = CAT_KEYS[spCat.getSelectedItemPosition()];
        results.clear(); page = 0; serverPage = 1; hasMore = false;
        fetch(false);
    }

    void fetch(final boolean advance) {
        final int sp = serverPage;
        final String q = curQ, cat = curCat;
        bg(() -> new JSONObject(Api.get("/api/search?q=" + enc(q) + "&category=" + cat + "&page=" + sp)), r -> {
            JSONObject o = (JSONObject) r;
            hasMore = false;
            int added = 0;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                JSONArray a = o.optJSONArray(it.next());
                if (a == null) continue;
                if (a.length() >= 20) hasMore = true;
                for (int i = 0; i < a.length(); i++) { results.add(a.getJSONObject(i)); added++; }
            }
            if (advance && added > 0) page++;
            searched = true;
            render();
        });
    }

    void render() {
        resultsBox.removeAllViews();
        if (!searched) {
            resultsBox.addView(tv("Choose a category, enter a search term and tap Search.", 13, MUTED, false));
            pager.setVisibility(View.GONE);
            return;
        }
        if (results.isEmpty()) {
            resultsBox.addView(tv("No results found.", 14, MUTED, false));
            pager.setVisibility(View.GONE);
            return;
        }
        int from = page * PER_PAGE, to = Math.min(from + PER_PAGE, results.size());
        for (int i = from; i < to; i++) resultsBox.addView(resultRow(results.get(i)), lp(-1, -2, 0, 8));
        tvPage.setText("Page " + (page + 1));
        enable(btnPrev, page > 0);
        enable(btnNext, to < results.size() || hasMore);
        pager.setVisibility(View.VISIBLE);
        resultsScroll.scrollTo(0, 0);
    }

    View resultRow(final JSONObject o) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.input_field);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(tv(o.optString("type").toUpperCase(Locale.ROOT), 10, BLUE, true));
        String t = clean(o, "title");
        left.addView(tv(t.isEmpty() ? "\u2014" : t, 15, INK, true));
        String[] keys = {"subtitle", "extra", "contacts"};
        for (String k : keys) {
            String s = clean(o, k);
            if (!s.isEmpty()) left.addView(tv(s, 13, MUTED, false));
        }
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        Button v = btn("View");
        v.setTextSize(13);
        v.setOnClickListener(x -> openDetail(o));
        row.addView(v, new LinearLayout.LayoutParams(dp(76), dp(40)));
        return row;
    }

    // ---------------------------------------------------------------- detail
    void openDetail(JSONObject o) {
        String t = o.optString("type");
        int id = o.optInt("id", 0);
        int pid = o.optInt("person_id", 0);
        String path, kind = t;
        if (t.equals("property"))      path = "/api/properties/" + id;
        else if (t.equals("person"))   path = "/api/persons/" + id;
        else if (t.equals("vehicle"))  path = "/api/vehicles/" + id;
        else if (t.equals("incident")) path = "/api/incidents/" + id;
        else if (t.equals("contact")) {
            if (pid == 0) { toast("This number is not linked to a person"); return; }
            path = "/api/persons/" + pid; kind = "person";
        } else return;
        final String fp = path, fk = kind;
        bg(() -> new JSONObject(Api.get(fp)), r -> showDetail(fk, (JSONObject) r));
    }

    void buildDetail() {
        panelDetail = new ScrollView(this);
        detailBox = col(16);
        panelDetail.addView(detailBox);
    }

    void showDetail(String type, JSONObject rec) {
        detailBox.removeAllViews();
        TextView back = tv("\u2039 Back to results", 14, BLUE, true);
        back.setPadding(0, dp(4), dp(12), dp(8));
        back.setOnClickListener(v -> show(panelSearch));
        detailBox.addView(back);

        String title;
        if (type.equals("property"))      title = clean(rec, "registered_owner");
        else if (type.equals("person"))   title = (clean(rec, "first_name") + " " + clean(rec, "last_name")).trim();
        else if (type.equals("vehicle"))  title = clean(rec, "plate");
        else                              title = clean(rec, "cas_number");

        detailBox.addView(tv(type.toUpperCase(Locale.ROOT) + " RECORD", 11, BLUE, true), lp(-2, -2, 4, 2));
        detailBox.addView(tv(title.isEmpty() ? "\u2014" : title, 22, INK, true));
        TextView badge = tv("CONFIDENTIAL", 10, Color.WHITE, true);
        badge.setBackgroundResource(R.drawable.btn_blue);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        detailBox.addView(badge, lp(-2, -2, 8, 4));

        String[] order = type.equals("property") ? ORD_PROPERTY : type.equals("person") ? ORD_PERSON
                : type.equals("vehicle") ? ORD_VEHICLE : ORD_INCIDENT;
        detailBox.addView(section("DETAILS"));
        addRows(detailBox, rec, order, null);

        Iterator<String> it = rec.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONArray arr = rec.optJSONArray(k);
            if (arr == null || arr.length() == 0) continue;
            detailBox.addView(section(SEC.containsKey(k) ? SEC.get(k) : pretty(k).toUpperCase(Locale.ROOT)));
            String[] only = k.equals("history") ? new String[]{"changed_at", "change_type", "note"} : null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                LinearLayout card = col(12);
                card.setBackgroundResource(R.drawable.input_field);
                addRows(card, item, ORD_CHILD, only);
                detailBox.addView(card, lp(-1, -2, 0, 8));
            }
        }

        boolean anySys = false;
        for (String k : SYS) if (!clean(rec, k).isEmpty()) anySys = true;
        if (anySys) {
            detailBox.addView(section("RECORD INFO"));
            for (String k : SYS) {
                String val = clean(rec, k);
                if (!val.isEmpty()) detailBox.addView(kv(pretty(k), fmt(val)));
            }
        }
        show(panelDetail);
        panelDetail.scrollTo(0, 0);
    }

    void addRows(LinearLayout parent, JSONObject o, String[] order, String[] only) {
        ArrayList<String> keys = new ArrayList<String>();
        if (order != null) for (String k : order) if (o.has(k)) keys.add(k);
        Iterator<String> it = o.keys();
        while (it.hasNext()) { String k = it.next(); if (!keys.contains(k)) keys.add(k); }
        for (String k : keys) {
            if (o.optJSONArray(k) != null || o.optJSONObject(k) != null) continue;
            if (k.equals("id") || k.endsWith("_id") || k.endsWith("_by") || k.equals("old_data") || k.equals("password")) continue;
            if (isSys(k)) continue;
            if (only != null) {
                boolean ok = false;
                for (String s : only) if (s.equals(k)) ok = true;
                if (!ok) continue;
            }
            String v = clean(o, k);
            if (v.isEmpty()) continue;
            if (k.equals("status") && v.equals("current")) continue;
            parent.addView(kv(pretty(k), fmt(v)));
        }
    }

    static boolean isSys(String k) {
        for (String s : SYS) if (s.equals(k)) return true;
        return false;
    }

    /** 2026-05-31T16:02:43.998710 -> 2026-05-31 16:02 */
    static String fmt(String v) {
        if (v.length() >= 16 && v.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}.*"))
            return v.substring(0, 10) + " " + v.substring(11, 16);
        return v;
    }

    View kv(String label, String value) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView l = tv(label.toUpperCase(Locale.ROOT), 11, BLUE, true);
        TextView v = tv(value, 15, INK, false);
        v.setTextIsSelectable(true);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 0.4f));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 0.6f));
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return wrap;
    }

    View section(String text) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(0, dp(20), 0, dp(6));
        s.addView(tv(text, 12, BLUE, true));
        View line = new View(this);
        line.setBackgroundColor(BLUE);
        s.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));
        return s;
    }

    // --------------------------------------------------------------- helpers
    void show(View p) {
        panelLogin.setVisibility(p == panelLogin ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility(p == panelSearch ? View.VISIBLE : View.GONE);
        panelDetail.setVisibility(p == panelDetail ? View.VISIBLE : View.GONE);
        current = p;
    }

    void bg(final Job j, final Done d) {
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                final Object r = j.run();
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    try { d.ok(r); } catch (Exception e) { err(e); }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> err(e));
            }
        }).start();
    }

    void err(Exception e) {
        loading.setVisibility(View.GONE);
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.equals("Unauthorized")) { show(panelLogin); m = "Session expired \u2013 please sign in again"; }
        toast(m);
    }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    void enable(Button b, boolean on) { b.setEnabled(on); b.setAlpha(on ? 1f : 0.4f); }

    static String clean(JSONObject o, String k) { return o.isNull(k) ? "" : o.optString(k, "").trim(); }

    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    static String pretty(String k) {
        if (LBL.containsKey(k)) return LBL.get(k);
        StringBuilder sb = new StringBuilder();
        for (String w : k.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    LinearLayout col(int padDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        return c;
    }

    LinearLayout.LayoutParams lp(int w, int h, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(0, dp(topDp), 0, dp(bottomDp));
        return p;
    }

    TextView tv(String t, int sp, int color, boolean bold) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(sp);
        x.setTextColor(color);
        if (bold) x.setTypeface(null, Typeface.BOLD);
        return x;
    }

    TextView label(String t) { return tv(t, 11, BLUE, true); }

    EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setBackgroundResource(R.drawable.input_field);
        e.setPadding(dp(16), 0, dp(16), 0);
        e.setHint(hint);
        e.setHintTextColor(0xFF9999BB);
        e.setTextColor(INK);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(inputType);
        return e;
    }

    Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackgroundResource(R.drawable.btn_blue);
        return b;
    }
}
