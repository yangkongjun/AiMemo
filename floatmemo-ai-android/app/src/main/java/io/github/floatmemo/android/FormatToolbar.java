package io.github.floatmemo.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

public final class FormatToolbar extends FrameLayout {
    private static final String[] SIZE_LABELS = {"一级字号（16）", "二级字号（20）", "三级字号（24）"};
    private static final int[] SIZES = {16, 20, 24};
    private static final String[] COLOR_LABELS = {"默认色", "红色", "橙色", "绿色", "蓝色", "紫色"};
    private static final long[] COLORS = {0, 0xFFAF3030L, 0xFF9C4A16L, 0xFF256348L, 0xFF245EB0L, 0xFF71389AL};

    private final FormatEditText editor;
    private final NoteRepository repository;
    private final String id;
    private final View normal;
    private final HorizontalScrollView formats;
    private final Button bold;
    private boolean enabled;
    private PopupWindow menu;

    private static final class Selection {
        final int start, end;
        final String text;

        Selection(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
    private interface FormatChange { void apply(Note note, int start, int end); }
    private interface Choice { void choose(Selection selection, int index); }

    public FormatToolbar(Context context, FormatEditText editor, NoteRepository repository, String id, View normal) {
        super(context);
        this.editor = editor;
        this.repository = repository;
        this.id = id;
        this.normal = normal;
        addView(normal, new LayoutParams(-1, -1));

        formats = new HorizontalScrollView(context);
        formats.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(context);
        formats.addView(row);
        addView(formats, new LayoutParams(-1, -1));
        formats.setVisibility(GONE);

        bold = add(row, "加粗", v -> {
            Selection selection = selection();
            apply(selection, (note, start, end) -> note.formats = TextFormat.set(note.formats, start, end,
                "bold", TextFormat.allBold(note.formats, start, end) ? 0 : 1));
        });
        bold.setTypeface(null, Typeface.BOLD);
        add(row, "字号 ▾", v -> showChoices(v, SIZE_LABELS, null, (selection, index) ->
            apply(selection, (note, start, end) -> note.formats = TextFormat.set(note.formats, start, end, "sizeSp", SIZES[index]))));
        add(row, "字号 −1", v -> changeSize(-1));
        add(row, "字号 +1", v -> changeSize(1));
        add(row, "字体颜色 ▾", v -> showChoices(v, COLOR_LABELS, COLORS, (selection, index) ->
            apply(selection, (note, start, end) -> note.formats = TextFormat.set(note.formats, start, end, "color", COLORS[index]))));
        add(row, "清除格式", v -> apply(selection(), (note, start, end) -> {
            for (String kind : new String[]{"bold", "size", "color"}) {
                note.formats = TextFormat.set(note.formats, start, end, kind, 0);
            }
        }));
        editor.selectionChanged = this::refresh;
    }

    private Button add(LinearLayout row, String label, OnClickListener click) {
        Button button = Ui.button(getContext(), label, false, click);
        button.setFocusable(false);
        button.setSingleLine();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -1);
        params.rightMargin = Ui.dp(getContext(), 4);
        row.addView(button, params);
        return button;
    }

    public void setFormattingEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && menu != null) menu.dismiss();
        refresh();
    }

    private Selection selection() {
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
        if (!enabled || !editor.isFocused() || start < 0 || end < 0 || start == end) return null;
        return new Selection(Math.min(start, end), Math.max(start, end), editor.getText().toString());
    }

    public void refresh() {
        if (menu != null) return;
        Selection selection = selection();
        boolean formatting = selection != null;
        if (formatting && formats.getVisibility() != VISIBLE) formats.scrollTo(0, 0);
        normal.setVisibility(formatting ? GONE : VISIBLE);
        formats.setVisibility(formatting ? VISIBLE : GONE);
        if (formatting) {
            Note note = repository.find(id);
            boolean active = note != null && TextFormat.allBold(note.formats, selection.start, selection.end);
            bold.setSelected(active);
            bold.setContentDescription(active ? "加粗，已开启" : "加粗");
            bold.setBackgroundTintList(ColorStateList.valueOf(active ? 0xFFC6DDCB : 0xFFE7EDE6));
        }
    }

    private void changeSize(int step) {
        apply(selection(), (note, start, end) -> note.formats = TextFormat.stepSize(note, start, end, step));
    }

    private void showChoices(View anchor, String[] labels, long[] colors, Choice choice) {
        Selection selected = selection();
        if (selected == null) return;
        LinearLayout rows = Ui.column(getContext());
        rows.setBackground(Ui.round(getContext(), 0xFFFFFFFF, 12));
        int width = Ui.dp(getContext(), 172);
        PopupWindow popup = new PopupWindow(rows, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        popup.setElevation(Ui.dp(getContext(), 8));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        for (int index = 0; index < labels.length; index++) {
            final int item = index;
            Button button = Ui.button(getContext(), labels[index], false, v -> {
                popup.dismiss();
                applyChoice(selected, item, choice);
            });
            if (colors != null && colors[index] != 0) button.setTextColor((int) colors[index]);
            rows.addView(button, new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 44)));
        }
        popup.setOnDismissListener(() -> {
            menu = null;
            refresh();
        });
        menu = popup;
        try {
            popup.showAsDropDown(anchor);
        } catch (RuntimeException e) {
            menu = null;
            refresh();
            Toast.makeText(getContext(), "无法打开格式菜单", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyChoice(Selection selection, int item, Choice choice) {
        if (enabled && editor.getText().toString().equals(selection.text)) {
            editor.requestFocus();
            editor.setSelection(selection.start, selection.end);
        }
        choice.choose(selection, item);
    }

    private void apply(Selection selection, FormatChange change) {
        if (selection == null) return;
        Note note = repository.find(id);
        if (!enabled || note == null || !note.content.equals(selection.text)
            || !editor.getText().toString().equals(selection.text)) {
            Toast.makeText(getContext(), "文字已改变，请重新选择", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean saved = repository.editMetadata(id, current -> {
            if (!current.content.equals(selection.text)) throw new IllegalArgumentException("文字已改变");
            change.apply(current, selection.start, selection.end);
        });
        if (!saved) {
            Toast.makeText(getContext(), repository.error, Toast.LENGTH_SHORT).show();
            return;
        }
        Note current = repository.find(id);
        if (current != null) TextStyleSpans.apply(editor, current);
        editor.setSelection(selection.start, selection.end);
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        if (menu != null) menu.dismiss();
        super.onDetachedFromWindow();
    }
}
