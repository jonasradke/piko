/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.text.format.DateFormat;

import java.util.Date;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.instagram.utils.Pref;

/**
 * Commitment mode: once locked, the selected distraction-free protections are forced on
 * and cannot be turned off until the lock expires. Unlocking early requires a cooling-off
 * period, so the decision to bring Reels back cannot be made on impulse.
 */
@SuppressWarnings("unused")
public class FocusLock {
    public static final long COOLING_OFF_MS = 24L * 60 * 60 * 1000;
    /** Prefix for the per target "lock this" preferences. */
    public static final String SELECTION_PREFIX = "focus_lock_sel_";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static long lockedUntil() {
        return parseLong(Pref.focusLockUntil());
    }

    public static long unlockRequestedAt() {
        return parseLong(Pref.focusLockUnlockRequestedAt());
    }

    // Intentionally does not consult SettingsStatus: the lock timestamp is only ever written by
    // this class, and hooks like HideNavigationButtonsPatch may read preferences at class-load time.
    public static boolean isLocked() {
        return System.currentTimeMillis() < lockedUntil();
    }

    /**
     * Whether the lock is actually holding something.
     *
     * A lock with nothing selected enforces nothing, so it must not gate the settings UI or
     * block resetting either, otherwise it just locks the user out for no reason. This is what
     * everything outside of enforcement should ask.
     */
    public static boolean isActive() {
        return isLocked() && hasSelection();
    }

    /** True while an unlock request is pending its cooling-off period. */
    public static boolean isUnlockPending() {
        return isActive() && unlockRequestedAt() > 0;
    }

    public static long unlockAvailableAt() {
        return unlockRequestedAt() + COOLING_OFF_MS;
    }

    public static boolean canUnlockNow() {
        return isUnlockPending() && System.currentTimeMillis() >= unlockAvailableAt();
    }

    // Enforcement. These are OR-ed in where the setting is read, so the underlying switch keeps
    // its stored value and simply cannot take effect while the lock is on.

    /** Whether the lock currently forces {@code setting} on. */
    public static boolean isForced(BooleanSetting setting) {
        return isForced(setting.key);
    }

    public static boolean isForced(String key) {
        return isLocked() && Pref.focusLockSelected(key);
    }

    /** The Reels navigation tab, which is not a switch of its own. */
    public static boolean blocksReels() {
        return isForced(FocusLockTargets.REELS_TAB_KEY);
    }

    /** True when at least one target is picked, so there is something to lock. */
    public static boolean hasSelection() {
        for (FocusLockTargets.Target target : FocusLockTargets.available()) {
            if (Pref.focusLockSelected(target.key)) return true;
        }
        return false;
    }

    public static boolean lock() {
        long until = System.currentTimeMillis() + FocusLockDuration.days() * DAY_MS;
        return Pref.setFocusLockUntil(String.valueOf(until))
                && Pref.setFocusLockUnlockRequestedAt("0");
    }

    public static boolean requestUnlock() {
        return Pref.setFocusLockUnlockRequestedAt(String.valueOf(System.currentTimeMillis()));
    }

    public static boolean cancelUnlockRequest() {
        return Pref.setFocusLockUnlockRequestedAt("0");
    }

    public static boolean unlock() {
        if (!canUnlockNow()) return false;
        return Pref.setFocusLockUntil("0")
                && Pref.setFocusLockUnlockRequestedAt("0");
    }

    public static String formatDate(long millis) {
        return DateFormat.format("yyyy-MM-dd HH:mm", new Date(millis)).toString();
    }

    /** Summary shown under the lock/unlock button. */
    public static String statusSummary() {
        if (!isActive()) {
            return str("piko_focus_lock_status_unlocked");
        }
        String until = formatDate(lockedUntil());
        if (canUnlockNow()) {
            return str("piko_focus_lock_status_unlock_ready");
        }
        if (isUnlockPending()) {
            return String.format(str("piko_focus_lock_status_unlock_pending"), formatDate(unlockAvailableAt()), until);
        }
        return String.format(str("piko_focus_lock_status_locked"), until);
    }

    public static String buttonTitle() {
        if (!isActive()) return str("piko_focus_lock_button_lock");
        if (canUnlockNow()) return str("piko_focus_lock_button_unlock");
        if (isUnlockPending()) return str("piko_focus_lock_button_cancel_unlock");
        return str("piko_focus_lock_button_request_unlock");
    }
}
