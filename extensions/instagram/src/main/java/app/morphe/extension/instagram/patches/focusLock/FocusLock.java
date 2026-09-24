/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.text.format.DateFormat;

import java.util.Date;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;

/**
 * Commitment mode: once locked, the selected distraction-free protections are forced on
 * and cannot be turned off until the lock expires. Unlocking early requires a cooling-off
 * period, so the decision to bring Reels back cannot be made on impulse.
 */
@SuppressWarnings("unused")
public class FocusLock {
    public static final long COOLING_OFF_MS = 24L * 60 * 60 * 1000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static long lockedUntil() {
        return parseLong(SharedPref.getStringPref(Settings.FOCUS_LOCK_UNTIL));
    }

    public static long unlockRequestedAt() {
        return parseLong(SharedPref.getStringPref(Settings.FOCUS_LOCK_UNLOCK_REQUESTED_AT));
    }

    // Intentionally does not consult SettingsStatus: the lock timestamp is only ever written by
    // this class, and hooks like HideNavigationButtonsPatch may read preferences at class-load time.
    public static boolean isLocked() {
        return System.currentTimeMillis() < lockedUntil();
    }

    /** True while an unlock request is pending its cooling-off period. */
    public static boolean isUnlockPending() {
        return isLocked() && unlockRequestedAt() > 0;
    }

    public static long unlockAvailableAt() {
        return unlockRequestedAt() + COOLING_OFF_MS;
    }

    public static boolean canUnlockNow() {
        return isUnlockPending() && System.currentTimeMillis() >= unlockAvailableAt();
    }

    // Enforcement helpers. These are OR-ed into the regular preference getters in Pref,
    // so the underlying switches keep their stored value and simply cannot take effect.
    public static boolean blocksReels() {
        return isLocked() && SharedPref.getBooleanPref(Settings.FOCUS_LOCK_BLOCK_REELS);
    }

    public static boolean blocksExplore() {
        return isLocked() && SharedPref.getBooleanPref(Settings.FOCUS_LOCK_BLOCK_EXPLORE);
    }

    /**
     * Reels *feed* endpoints, so the Reels tab stays empty even if reached via a deep link.
     *
     * Deliberately excludes the endpoints that serve a single reel, so a reel shared in a DM, a
     * story or a notification still opens. /clips/chaining/ is one of those: it seeds the viewer
     * when a single reel is opened from a permalink, not just the "up next" chain. Swiping onward
     * is already prevented by "Disable Reels scrolling".
     */
    public static boolean isReelsFeedPath(String path) {
        return path.contains("/clips/home/")
                || path.contains("/clips/discover/")
                || path.contains("/clips/trending/")
                || path.contains("/clips/explore_reels/")
                || path.contains("/clips/home_connected/");
    }

    public static boolean lock() {
        long days = parseLong(SharedPref.getStringPref(Settings.FOCUS_LOCK_DURATION_DAYS));
        if (days <= 0) days = 7;
        long until = System.currentTimeMillis() + days * DAY_MS;
        return SharedPref.setStringPref(Settings.FOCUS_LOCK_UNTIL.key, String.valueOf(until))
                && SharedPref.setStringPref(Settings.FOCUS_LOCK_UNLOCK_REQUESTED_AT.key, "0");
    }

    public static boolean requestUnlock() {
        return SharedPref.setStringPref(
                Settings.FOCUS_LOCK_UNLOCK_REQUESTED_AT.key,
                String.valueOf(System.currentTimeMillis())
        );
    }

    public static boolean cancelUnlockRequest() {
        return SharedPref.setStringPref(Settings.FOCUS_LOCK_UNLOCK_REQUESTED_AT.key, "0");
    }

    public static boolean unlock() {
        if (!canUnlockNow()) return false;
        return SharedPref.setStringPref(Settings.FOCUS_LOCK_UNTIL.key, "0")
                && SharedPref.setStringPref(Settings.FOCUS_LOCK_UNLOCK_REQUESTED_AT.key, "0");
    }

    public static String formatDate(long millis) {
        return DateFormat.format("yyyy-MM-dd HH:mm", new Date(millis)).toString();
    }

    /** Summary shown under the lock/unlock button. */
    public static String statusSummary() {
        if (!isLocked()) {
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
        if (!isLocked()) return str("piko_focus_lock_button_lock");
        if (canUnlockNow()) return str("piko_focus_lock_button_unlock");
        if (isUnlockPending()) return str("piko_focus_lock_button_cancel_unlock");
        return str("piko_focus_lock_button_request_unlock");
    }
}
