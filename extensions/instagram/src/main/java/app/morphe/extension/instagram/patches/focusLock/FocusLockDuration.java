/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ResourceUtils;

/** How long a lock lasts: one of the presets, or a number of days the user types in. */
public final class FocusLockDuration {

    /** Picked from the list to type a number of days instead. */
    public static final String CUSTOM_VALUE = "custom";

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 365;
    private static final int FALLBACK_DAYS = 7;

    private FocusLockDuration() {
    }

    public static int days() {
        return clamp(parse(Pref.focusLockDurationDays()));
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return FALLBACK_DAYS;
        }
    }

    private static int clamp(int days) {
        if (days < MIN_DAYS) return FALLBACK_DAYS;
        return Math.min(days, MAX_DAYS);
    }

    /** The preset label when the value is one of them, otherwise the plain number of days. */
    public static String summary() {
        int days = days();
        String stored = String.valueOf(days);
        try {
            CharSequence[] values = ResourceUtils.getStringArray("piko_array_focus_lock_duration_val");
            CharSequence[] labels = ResourceUtils.getStringArray("piko_array_focus_lock_duration");
            for (int i = 0; i < values.length && i < labels.length; i++) {
                if (stored.contentEquals(values[i])) return labels[i].toString();
            }
        } catch (Exception ignored) {
            // Fall through to the plain day count.
        }
        return String.format(str("piko_focus_lock_duration_days"), days);
    }

    /** Asks for a number of days. {@code onSaved} runs only when a value was stored. */
    public static void showCustomDialog(Context context, Runnable onSaved) {
        Context dialogContext = InstagramPreferenceStyle.dialogContext(context);

        EditText input = new EditText(dialogContext);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(days()));
        input.setText(String.valueOf(days()));
        input.setSelection(input.getText().length());

        int padding = InstagramPreferenceStyle.dp(dialogContext, 20);
        FrameLayout container = new FrameLayout(dialogContext);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new AlertDialog.Builder(dialogContext)
                .setTitle(str("piko_focus_lock_duration_custom_title"))
                .setMessage(String.format(str("piko_focus_lock_duration_custom_desc"), MIN_DAYS, MAX_DAYS))
                .setView(container)
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                    int days = clamp(parse(input.getText().toString()));
                    if (Pref.setFocusLockDurationDays(String.valueOf(days)) && onSaved != null) {
                        onSaved.run();
                    }
                })
                .show();
    }
}
