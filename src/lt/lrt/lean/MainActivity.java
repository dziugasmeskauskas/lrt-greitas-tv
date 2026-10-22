package lt.lrt.lean;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private List<VideoItem.Section> sections;
    private ListView navListView;
    private NavAdapter navAdapter;

    private LinearLayout searchContainer;
    private TextView searchQueryDisplay;
    private StringBuilder searchBuffer = new StringBuilder();

    private TextView heroCategoryBadge;
    private TextView heroMetaText;
    private TextView heroTitleText;
    private TextView heroDescText;
    private TextView statusText;
    private TextView clockText;
    private ProgressBar loadingBar;

    private GridView gridView;
    private CardAdapter cardAdapter;
    private final List<VideoItem> currentItems = new ArrayList<>();

    private int activeSectionIndex = 0;
    private String currentFeedUrl = "";
    private String currentNextUrl = null;
    private boolean isLoadingMore = false;
    private String customCategoryTitle = null;

    private int selectedGridIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private Runnable pendingSectionLoad;

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            if (clockText != null) {
                String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                clockText.setText(now);
            }
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lrt_lean_prefs", Context.MODE_PRIVATE);
        sections = LrtApi.getDefaultSections();

        buildUi();
        ensureBluetoothSpeakerConnected();

        // Pre-fetch core sections in parallel so switching is 0ms instant
        for (int i = 0; i < Math.min(6, sections.size()); i++) {
            VideoItem.Section s = sections.get(i);
            if (!s.isSearch && s.url != null && !s.url.isEmpty()) {
                LrtApi.prefetchUrl(s.url);
            }
        }

        selectSection(0, false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF070E1A);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ================= LEFT SIDEBAR =================
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(0xFF0C1729);
        sidebar.setPadding(dp(14), dp(18), dp(14), dp(14));
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(
                dp(250),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        sidebar.setLayoutParams(sideParams);

        // Brand Header
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(8), dp(2), dp(8), dp(12));

        TextView brandLogo = new TextView(this);
        brandLogo.setText("LRT");
        brandLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        brandLogo.setTypeface(Typeface.DEFAULT_BOLD);
        brandLogo.setTextColor(Color.WHITE);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(0xFF0077C8);
        logoBg.setCornerRadius(dp(6));
        brandLogo.setBackground(logoBg);
        brandLogo.setPadding(dp(10), dp(3), dp(10), dp(3));
        brandRow.addView(brandLogo);

        TextView brandSub = new TextView(this);
        brandSub.setText("  GREITAS");
        brandSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        brandSub.setTypeface(Typeface.DEFAULT_BOLD);
        brandSub.setTextColor(0xFF00E5FF);
        brandRow.addView(brandSub);

        clockText = new TextView(this);
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clockText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockText.setTextColor(0xFF90A4AE);
        clockText.setGravity(Gravity.END);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        clockText.setLayoutParams(clockParams);
        brandRow.addView(clockText);

        sidebar.addView(brandRow);

        navListView = new ListView(this);
        navListView.setDivider(null);
        navListView.setDividerHeight(dp(3));
        navListView.setSelector(android.R.color.transparent);
        navListView.setVerticalScrollBarEnabled(false);
        navListView.setFocusable(true);
        navListView.setFocusableInTouchMode(true);
        navAdapter = new NavAdapter();
        navListView.setAdapter(navAdapter);

        navListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, final int position, long id) {
                navAdapter.notifyDataSetChanged();
                if (position != activeSectionIndex || customCategoryTitle != null) {
                    if (pendingSectionLoad != null) {
                        handler.removeCallbacks(pendingSectionLoad);
                    }
                    pendingSectionLoad = new Runnable() {
                        @Override
                        public void run() {
                            selectSection(position, false);
                        }
                    };
                    handler.postDelayed(pendingSectionLoad, 130);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        navListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (pendingSectionLoad != null) {
                    handler.removeCallbacks(pendingSectionLoad);
                }
                selectSection(position, true);
            }
        });

        navListView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (sections.get(activeSectionIndex).isSearch && searchContainer.getVisibility() == View.VISIBLE) {
                        searchContainer.requestFocus();
                        return true;
                    }
                    if (!currentItems.isEmpty()) {
                        gridView.requestFocus();
                        if (selectedGridIndex < 0) {
                            gridView.setSelection(0);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        navListView.setLayoutParams(navParams);
        sidebar.addView(navListView);

        root.addView(sidebar);

        // ================= RIGHT MAIN CONTENT =================
        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        mainCol.setPadding(dp(20), dp(16), dp(24), dp(12));
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        );
        mainCol.setLayoutParams(mainParams);

        // 1. Hero Detail Banner
        LinearLayout heroBox = new LinearLayout(this);
        heroBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF10213A, 0xFF0B1628}
        );
        heroBg.setCornerRadius(dp(10));
        heroBg.setStroke(dp(1), 0xFF1E3A5F);
        heroBox.setBackground(heroBg);
        heroBox.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        heroParams.bottomMargin = dp(12);
        heroBox.setLayoutParams(heroParams);

        LinearLayout heroTopRow = new LinearLayout(this);
        heroTopRow.setOrientation(LinearLayout.HORIZONTAL);
        heroTopRow.setGravity(Gravity.CENTER_VERTICAL);

        heroCategoryBadge = new TextView(this);
        heroCategoryBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        heroCategoryBadge.setTypeface(Typeface.DEFAULT_BOLD);
        heroCategoryBadge.setTextColor(Color.WHITE);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xFF0077C8);
        badgeBg.setCornerRadius(dp(4));
        heroCategoryBadge.setBackground(badgeBg);
        heroCategoryBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        heroCategoryBadge.setText("LRT GREITAS");
        heroTopRow.addView(heroCategoryBadge);

        heroMetaText = new TextView(this);
        heroMetaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heroMetaText.setTextColor(0xFF00E5FF);
        heroMetaText.setPadding(dp(12), 0, 0, 0);
        heroMetaText.setSingleLine(true);
        heroMetaText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroMetaText.setLayoutParams(metaParams);
        heroTopRow.addView(heroMetaText);

        loadingBar = new ProgressBar(this);
        LinearLayout.LayoutParams lbParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        loadingBar.setLayoutParams(lbParams);
        loadingBar.setVisibility(View.GONE);
        heroTopRow.addView(loadingBar);

        heroBox.addView(heroTopRow);

        heroTitleText = new TextView(this);
        heroTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        heroTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitleText.setTextColor(Color.WHITE);
        heroTitleText.setSingleLine(true);
        heroTitleText.setEllipsize(TextUtils.TruncateAt.END);
        heroTitleText.setPadding(0, dp(4), 0, dp(2));
        heroTitleText.setText("Pasirinkite laidą arba tiesioginę transliaciją");
        heroBox.addView(heroTitleText);

        heroDescText = new TextView(this);
        heroDescText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heroDescText.setTextColor(0xFFB0C4DE);
        heroDescText.setMaxLines(2);
        heroDescText.setMinLines(2);
        heroDescText.setEllipsize(TextUtils.TruncateAt.END);
        heroDescText.setText("Naudokite pultelio rodykles. Paspauskite OK norėdami žiūrėti, arba palaikykite OK, kad atidarytumėte visas tos laidos serijas.");
        heroBox.addView(heroDescText);

        mainCol.addView(heroBox);

        // 2. Built-in TV Search Bar & On-Screen Keyboard (Visible in Search section)
        buildSearchPanel(mainCol);

        // 3. Status message
        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setTextColor(0xFF90A4AE);
        statusText.setPadding(dp(8), dp(8), dp(8), dp(8));
        statusText.setVisibility(View.GONE);
        mainCol.addView(statusText);

        // 4. 3-Column Recycled Card GridView
        gridView = new GridView(this);
        gridView.setNumColumns(3);
        gridView.setHorizontalSpacing(dp(14));
        gridView.setVerticalSpacing(dp(14));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setSelector(android.R.color.transparent);
        gridView.setClipToPadding(false);
        gridView.setFocusable(true);
        gridView.setFocusableInTouchMode(true);
        cardAdapter = new CardAdapter();
        gridView.setAdapter(cardAdapter);

        gridView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGridIndex = position;
                cardAdapter.notifyDataSetChanged();
                if (position >= 0 && position < currentItems.size()) {
                    updateHeroWithItem(currentItems.get(position));
                    if (position >= currentItems.size() - 6 && currentNextUrl != null && !isLoadingMore) {
                        loadMoreItems();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        gridView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && selectedGridIndex < 0 && !currentItems.isEmpty()) {
                    selectedGridIndex = 0;
                }
                cardAdapter.notifyDataSetChanged();
                if (hasFocus && selectedGridIndex >= 0 && selectedGridIndex < currentItems.size()) {
                    updateHeroWithItem(currentItems.get(selectedGridIndex));
                }
            }
        });

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentItems.size()) {
                    openPlayer(currentItems.get(position));
                }
            }
        });

        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentItems.size()) {
                    VideoItem item = currentItems.get(position);
                    if (item.categoryId != null && !item.categoryId.isEmpty() && !item.isLive) {
                        openCategoryDrilldown(item.categoryId, item.categoryTitle != null && !item.categoryTitle.isEmpty() ? item.categoryTitle : item.title);
                        return true;
                    }
                }
                return false;
            }
        });

        gridView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = gridView.getSelectedItemPosition();
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && pos >= 0 && (pos % 3 == 0)) {
                        navListView.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                        if (pos >= 0 && pos < currentItems.size()) {
                            VideoItem item = currentItems.get(pos);
                            if (item.categoryId != null && !item.categoryId.isEmpty() && !item.isLive) {
                                openCategoryDrilldown(item.categoryId, item.categoryTitle);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        gridView.setLayoutParams(gridParams);
        mainCol.addView(gridView);

        root.addView(mainCol);
        setContentView(root);
    }

    private void buildSearchPanel(LinearLayout parent) {
        searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0E1D33);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF23456E);
        searchContainer.setBackground(bg);
        searchContainer.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scParams.bottomMargin = dp(10);
        searchContainer.setLayoutParams(scParams);
        searchContainer.setVisibility(View.GONE);

        // Top Search Input Row
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        searchQueryDisplay = new TextView(this);
        searchQueryDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchQueryDisplay.setTypeface(Typeface.DEFAULT_BOLD);
        searchQueryDisplay.setTextColor(Color.WHITE);
        GradientDrawable qBg = new GradientDrawable();
        qBg.setColor(0xFF07101E);
        qBg.setCornerRadius(dp(6));
        qBg.setStroke(dp(1), 0xFF00B4FF);
        searchQueryDisplay.setBackground(qBg);
        searchQueryDisplay.setPadding(dp(14), dp(8), dp(14), dp(8));
        searchQueryDisplay.setHint("Surinkite paieškos frazę žemiau...");
        searchQueryDisplay.setHintTextColor(0xFF607D8B);
        LinearLayout.LayoutParams qParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        qParams.rightMargin = dp(8);
        searchQueryDisplay.setLayoutParams(qParams);
        inputRow.addView(searchQueryDisplay);

        inputRow.addView(createActionKeyButton("⌨ Klaviatūra", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSystemSearchDialog();
            }
        }));

        inputRow.addView(createActionKeyButton("TARPAS", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchBuffer.append(" ");
                onSearchBufferUpdated();
            }
        }));

        inputRow.addView(createActionKeyButton("⌫ Trinti", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchBuffer.length() > 0) {
                    searchBuffer.deleteCharAt(searchBuffer.length() - 1);
                    onSearchBufferUpdated();
                }
            }
        }));

        inputRow.addView(createActionKeyButton("✕ Išvalyti", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchBuffer.setLength(0);
                onSearchBufferUpdated();
            }
        }));

        searchContainer.addView(inputRow);

        // Quick Lithuanian Alphabet Row
        final String[] keys = {
                "A", "B", "C", "Č", "D", "E", "Ė", "F", "G", "H", "I", "Y", "J", "K", "L", "M",
                "N", "O", "P", "R", "S", "Š", "T", "U", "Ū", "V", "Z", "Ž"
        };
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setPadding(0, dp(8), 0, dp(6));

        for (final String k : keys) {
            TextView kb = createMiniKeyButton(k, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchBuffer.append(k.toLowerCase(Locale.ROOT));
                    onSearchBufferUpdated();
                }
            });
            keyRow.addView(kb);
        }
        HorizontalScrollView keyScroll = new HorizontalScrollView(this);
        keyScroll.setHorizontalScrollBarEnabled(false);
        keyScroll.addView(keyRow);
        searchContainer.addView(keyScroll);

        // Quick Preset Chips Row
        final String[] presets = {
                "Auksinis protas", "Tūkstantmečio vaikai", "Kas ir kodėl", "Panorama",
                "Dviračio žinios", "Stonkus", "Krepšinis", "Koncertas", "Dokumentika"
        };
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final String preset : presets) {
            TextView chip = createActionKeyButton("🔍 " + preset, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchBuffer.setLength(0);
                    searchBuffer.append(preset);
                    onSearchBufferUpdated();
                }
            });
            chipRow.addView(chip);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chipRow);
        searchContainer.addView(chipScroll);

        parent.addView(searchContainer);
    }

    private TextView createMiniKeyButton(String label, View.OnClickListener listener) {
        final TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setPadding(dp(9), dp(5), dp(9), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = dp(4);
        btn.setLayoutParams(lp);
        styleFocusableButton(btn, false);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleFocusableButton(btn, hasFocus);
            }
        });
        btn.setOnClickListener(listener);
        return btn;
    }

    private TextView createActionKeyButton(String label, View.OnClickListener listener) {
        final TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = dp(6);
        btn.setLayoutParams(lp);
        styleFocusableButton(btn, false);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleFocusableButton(btn, hasFocus);
            }
        });
        btn.setOnClickListener(listener);
        return btn;
    }

    private void styleFocusableButton(TextView btn, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(6));
        if (focused) {
            d.setColor(0xFF00B4FF);
            d.setStroke(dp(2), Color.WHITE);
            btn.setTextColor(0xFF040B14);
        } else {
            d.setColor(0xFF182C49);
            d.setStroke(dp(1), 0xFF2D4F7C);
            btn.setTextColor(Color.WHITE);
        }
        btn.setBackground(d);
    }

    private final Runnable triggerSearchRunnable = new Runnable() {
        @Override
        public void run() {
            String q = searchBuffer.toString().trim();
            if (q.length() >= 2) {
                loadFeedUrl(LrtApi.buildSearchUrl(q), false, "PAIEŠKA: " + q.toUpperCase());
            }
        }
    };

    private void onSearchBufferUpdated() {
        String q = searchBuffer.toString();
        searchQueryDisplay.setText(q);
        handler.removeCallbacks(triggerSearchRunnable);
        if (q.trim().length() >= 2) {
            handler.postDelayed(triggerSearchRunnable, 320);
        }
    }

    private void showSystemSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Paieška LRT mediatekoje");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchBuffer.toString());
        input.setSelection(input.getText().length());
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Ieškoti", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String q = input.getText().toString().trim();
                searchBuffer.setLength(0);
                searchBuffer.append(q);
                onSearchBufferUpdated();
            }
        });
        builder.setNegativeButton("Atšaukti", null);

        final AlertDialog dialog = builder.create();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String q = input.getText().toString().trim();
                searchBuffer.setLength(0);
                searchBuffer.append(q);
                onSearchBufferUpdated();
                dialog.dismiss();
                return true;
            }
        });
        dialog.show();
        input.requestFocus();
    }

    private void selectSection(int index, boolean focusGridOrRefresh) {
        if (index < 0 || index >= sections.size()) return;
        activeSectionIndex = index;
        customCategoryTitle = null;
        navAdapter.notifyDataSetChanged();

        VideoItem.Section sec = sections.get(index);
        heroCategoryBadge.setText(sec.title.replaceAll("^[^A-Za-zĄČĘĖĮŠŲŪŽąčęėįšųūž]+", "").trim().toUpperCase());
        heroMetaText.setText(sec.subtitle);

        if (sec.isSearch) {
            searchContainer.setVisibility(View.VISIBLE);
            if (searchBuffer.toString().trim().length() >= 2) {
                loadFeedUrl(LrtApi.buildSearchUrl(searchBuffer.toString().trim()), true, "PAIEŠKA");
            } else {
                // Show popular items underneath search by default so screen is never empty
                loadFeedUrl(LrtApi.BASE_API + "/new/?count=18&order=read_count&days=7", true, "POPULIARIAUSI");
            }
            if (focusGridOrRefresh) {
                searchContainer.requestFocus();
            }
        } else {
            searchContainer.setVisibility(View.GONE);
            boolean useCache = !sec.isLive && !focusGridOrRefresh;
            if (sec.isLive && focusGridOrRefresh) {
                LrtApi.clearFeedCache(sec.url);
            }
            loadFeedUrl(sec.url, useCache, null);
            if (focusGridOrRefresh && !currentItems.isEmpty()) {
                gridView.requestFocus();
            }
        }
    }

    private void openCategoryDrilldown(String categoryId, String categoryTitle) {
        customCategoryTitle = categoryTitle != null ? categoryTitle : "Laidos įrašai";
        searchContainer.setVisibility(View.GONE);
        heroCategoryBadge.setText(customCategoryTitle.toUpperCase());
        heroMetaText.setText("Visos šios laidos serijos LRT mediatekoje (ATGAL – grįžti)");
        String url = LrtApi.buildCategoryUrl(categoryId);
        loadFeedUrl(url, true, customCategoryTitle);
    }

    private void loadFeedUrl(final String url, boolean useCache, final String overrideLabel) {
        if (url == null || url.isEmpty()) return;
        currentFeedUrl = url;
        currentNextUrl = null;
        isLoadingMore = false;
        loadingBar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);

        LrtApi.fetchFeed(url, useCache, new LrtApi.Callback<VideoItem.FeedPage>() {
            @Override
            public void onResult(VideoItem.FeedPage page, Exception error) {
                if (!url.equals(currentFeedUrl)) return;
                loadingBar.setVisibility(View.GONE);

                if (error != null || page == null || page.items == null) {
                    statusText.setText("Nepavyko užkrauti sąrašo. Patikrinkite ryšį ir spauskite OK.");
                    statusText.setVisibility(View.VISIBLE);
                    return;
                }

                currentItems.clear();
                currentItems.addAll(page.items);
                currentNextUrl = page.nextUrl;
                selectedGridIndex = currentItems.isEmpty() ? -1 : 0;
                cardAdapter.notifyDataSetChanged();

                if (currentItems.isEmpty()) {
                    statusText.setText("Įrašų nerasta.");
                    statusText.setVisibility(View.VISIBLE);
                } else {
                    gridView.setSelection(0);
                    updateHeroWithItem(currentItems.get(0));
                }
            }
        });
    }

    private void loadMoreItems() {
        if (isLoadingMore || currentNextUrl == null || currentNextUrl.isEmpty()) return;
        isLoadingMore = true;
        final String pageUrl = currentNextUrl;
        loadingBar.setVisibility(View.VISIBLE);

        LrtApi.fetchFeed(pageUrl, true, new LrtApi.Callback<VideoItem.FeedPage>() {
            @Override
            public void onResult(VideoItem.FeedPage page, Exception error) {
                isLoadingMore = false;
                loadingBar.setVisibility(View.GONE);
                if (page != null && page.items != null && !page.items.isEmpty()) {
                    currentItems.addAll(page.items);
                    currentNextUrl = page.nextUrl;
                    cardAdapter.notifyDataSetChanged();
                } else {
                    currentNextUrl = null;
                }
            }
        });
    }

    private void updateHeroWithItem(VideoItem item) {
        if (item == null) return;
        String badge = item.categoryTitle != null && !item.categoryTitle.isEmpty()
                ? item.categoryTitle
                : (item.isLive ? "TIESIOGIAI" : "LRT MEDIATEKA");
        heroCategoryBadge.setText(badge.toUpperCase());

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(4));
        badgeBg.setColor(item.isLive ? 0xFFE53935 : 0xFF0077C8);
        heroCategoryBadge.setBackground(badgeBg);

        StringBuilder meta = new StringBuilder();
        if (item.dateText != null && !item.dateText.isEmpty()) {
            meta.append(item.dateText);
        }
        if (item.durationText != null && !item.durationText.isEmpty()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(item.isLive ? "Eteryje: " : "Trukmė: ").append(item.durationText);
        }
        if (!item.isLive && item.id != null) {
            int savedMs = prefs.getInt("pos_" + item.id, 0);
            if (savedMs > 15000) {
                if (meta.length() > 0) meta.append("  •  ");
                meta.append("▶ Tęsti nuo ").append(LrtApi.formatDuration(savedMs / 1000));
            }
        }
        if (!item.isLive && item.categoryId != null && !item.categoryId.isEmpty() && customCategoryTitle == null) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append("Ilgas OK: visos laidos serijos");
        }
        heroMetaText.setText(meta.toString());

        heroTitleText.setText(item.title != null ? item.title : "");
        if (item.description != null && !item.description.isEmpty()) {
            heroDescText.setText(item.description);
        } else if (item.isLive && item.channelTitle != null) {
            heroDescText.setText("Tiesioginė " + item.channelTitle + " transliacija aukščiausia kokybe.");
        } else {
            heroDescText.setText("Paspauskite OK norėdami pradėti žiūrėti įrašą.");
        }
    }

    private void openPlayer(VideoItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_ID, item.id);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
        intent.putExtra(PlayerActivity.EXTRA_DESC, item.description);
        intent.putExtra(PlayerActivity.EXTRA_CATEGORY, item.categoryTitle);
        intent.putExtra(PlayerActivity.EXTRA_STREAM_URL, item.streamUrl);
        intent.putExtra(PlayerActivity.EXTRA_IS_LIVE, item.isLive);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_CODE, item.channelCode);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (customCategoryTitle != null) {
            customCategoryTitle = null;
            selectSection(activeSectionIndex, true);
            return;
        }
        if (gridView != null && gridView.hasFocus()) {
            navListView.requestFocus();
            return;
        }
        if (searchContainer != null && searchContainer.hasFocus()) {
            navListView.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(clockTicker);
        if (cardAdapter != null) {
            cardAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTicker);
        super.onPause();
    }

    // ================= SIDEBAR ADAPTER =================
    private class NavAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return sections.size();
        }

        @Override
        public Object getItem(int position) {
            return sections.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(MainActivity.this);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(dp(14), dp(10), dp(14), dp(10));
            }

            VideoItem.Section sec = sections.get(position);
            tv.setText(sec.title);

            boolean isSelectedSection = (position == activeSectionIndex);
            boolean listHasFocus = navListView.hasFocus();
            int focusedPos = navListView.getSelectedItemPosition();
            boolean isFocusedRow = listHasFocus && (focusedPos == position);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));

            if (isFocusedRow) {
                bg.setColor(0xFF00B4FF);
                tv.setTextColor(0xFF040B14);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else if (isSelectedSection) {
                bg.setColor(0xFF16325B);
                bg.setStroke(dp(1), 0xFF00B4FF);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(Color.TRANSPARENT);
                tv.setTextColor(0xFFB0C4DE);
                tv.setTypeface(Typeface.DEFAULT);
            }
            tv.setBackground(bg);
            return tv;
        }
    }

    // ================= RECYCLED CARD ADAPTER =================
    private static class CardHolder {
        LinearLayout root;
        ImageView thumb;
        TextView topBadge;
        TextView durationBadge;
        View progressTrack;
        View progressFill;
        TextView title;
        TextView subtitle;
    }

    private class CardAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return currentItems.size();
        }

        @Override
        public Object getItem(int position) {
            return currentItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CardHolder h;
            if (convertView == null) {
                h = new CardHolder();

                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(6), dp(6), dp(6), dp(8));
                card.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(202)
                ));

                FrameLayout thumbBox = new FrameLayout(MainActivity.this);
                LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(124)
                );
                thumbBox.setLayoutParams(tbParams);
                thumbBox.setBackgroundColor(0xFF060C17);

                ImageView img = new ImageView(MainActivity.this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                thumbBox.addView(img);

                TextView topBadge = new TextView(MainActivity.this);
                topBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                topBadge.setTypeface(Typeface.DEFAULT_BOLD);
                topBadge.setTextColor(Color.WHITE);
                topBadge.setSingleLine(true);
                topBadge.setEllipsize(TextUtils.TruncateAt.END);
                topBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
                FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.START
                );
                topLp.setMargins(dp(6), dp(6), dp(6), 0);
                topBadge.setLayoutParams(topLp);
                thumbBox.addView(topBadge);

                TextView durBadge = new TextView(MainActivity.this);
                durBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                durBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                durBadge.setTextColor(Color.WHITE);
                GradientDrawable durBg = new GradientDrawable();
                durBg.setColor(0xCC050A14);
                durBg.setCornerRadius(dp(4));
                durBadge.setBackground(durBg);
                durBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
                FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.END
                );
                durLp.setMargins(0, 0, dp(6), dp(6));
                durBadge.setLayoutParams(durLp);
                thumbBox.addView(durBadge);

                FrameLayout progTrack = new FrameLayout(MainActivity.this);
                progTrack.setBackgroundColor(0x66000000);
                FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(4),
                        Gravity.BOTTOM
                );
                progTrack.setLayoutParams(ptLp);

                View progFill = new View(MainActivity.this);
                progFill.setBackgroundColor(0xFF00E5FF);
                progFill.setLayoutParams(new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
                progTrack.addView(progFill);
                thumbBox.addView(progTrack);

                card.addView(thumbBox);

                TextView title = new TextView(MainActivity.this);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.WHITE);
                title.setMaxLines(2);
                title.setMinLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setPadding(dp(4), dp(6), dp(4), 0);
                card.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                sub.setTextColor(0xFF8AB4F8);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                sub.setPadding(dp(4), dp(2), dp(4), 0);
                card.addView(sub);

                h.root = card;
                h.thumb = img;
                h.topBadge = topBadge;
                h.durationBadge = durBadge;
                h.progressTrack = progTrack;
                h.progressFill = progFill;
                h.title = title;
                h.subtitle = sub;
                card.setTag(h);
                convertView = card;
            } else {
                h = (CardHolder) convertView.getTag();
            }

            VideoItem item = currentItems.get(position);
            boolean isFocusedCard = gridView.hasFocus() && (position == selectedGridIndex);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(dp(8));
            if (isFocusedCard) {
                cardBg.setColor(0xFF1A365D);
                cardBg.setStroke(dp(3), 0xFF00E5FF);
                h.title.setTextColor(Color.WHITE);
            } else {
                cardBg.setColor(0xFF0F1E36);
                cardBg.setStroke(dp(1), 0xFF1E3354);
                h.title.setTextColor(0xFFE2E8F0);
            }
            h.root.setBackground(cardBg);

            h.title.setText(item.title != null ? item.title : "");

            String badgeLabel = item.isLive
                    ? (item.channelTitle != null && !item.channelTitle.isEmpty() ? "🔴 " + item.channelTitle : "🔴 TIESIOGIAI")
                    : (item.categoryTitle != null ? item.categoryTitle : "");
            if (!badgeLabel.isEmpty()) {
                h.topBadge.setVisibility(View.VISIBLE);
                h.topBadge.setText(badgeLabel);
                GradientDrawable tbBg = new GradientDrawable();
                tbBg.setCornerRadius(dp(4));
                tbBg.setColor(item.isLive ? 0xD9D32F2F : 0xD90066B2);
                h.topBadge.setBackground(tbBg);
            } else {
                h.topBadge.setVisibility(View.GONE);
            }

            if (item.durationText != null && !item.durationText.isEmpty()) {
                h.durationBadge.setVisibility(View.VISIBLE);
                h.durationBadge.setText(item.durationText);
            } else {
                h.durationBadge.setVisibility(View.GONE);
            }

            int savedPos = (!item.isLive && item.id != null) ? prefs.getInt("pos_" + item.id, 0) : 0;
            int savedDur = (!item.isLive && item.id != null) ? prefs.getInt("dur_" + item.id, item.durationSeconds * 1000) : 0;
            if (savedPos > 15000 && savedDur > 0) {
                h.progressTrack.setVisibility(View.VISIBLE);
                int pctWidth = Math.max(dp(12), Math.min(dp(260), (int) (((long) savedPos * dp(260)) / savedDur)));
                h.progressFill.setLayoutParams(new FrameLayout.LayoutParams(pctWidth, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                h.progressTrack.setVisibility(View.GONE);
            }

            if (item.isLive && item.broadcastTime != null && !item.broadcastTime.isEmpty()) {
                h.subtitle.setText("Eteryje: " + item.broadcastTime);
            } else if (item.dateText != null && !item.dateText.isEmpty()) {
                h.subtitle.setText(item.dateText);
            } else {
                h.subtitle.setText("");
            }

            LrtApi.loadThumbnail(item.thumbnailUrl, h.thumb);
            return convertView;
        }
    }

    private void ensureBluetoothSpeakerConnected() {
        try {
            Intent svc = new Intent(this, BtKeepAliveService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return;
            final Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return;

            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        List<BluetoothDevice> connected = proxy.getConnectedDevices();
                        if (connected == null || connected.isEmpty()) {
                            Method connectMethod = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                            for (BluetoothDevice dev : bonded) {
                                connectMethod.invoke(proxy, dev);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
