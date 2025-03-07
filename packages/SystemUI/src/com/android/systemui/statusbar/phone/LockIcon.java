/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.policy.AccessibilityController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;

import com.android.internal.util.custom.FodUtils;

/**
 * Manages the different states and animations of the unlock icon.
 */
public class LockIcon extends KeyguardAffordanceView implements OnUserInfoChangedListener {

    private static final int FP_DRAW_OFF_TIMEOUT = 800;

    private static final int STATE_LOCKED = 0;
    private static final int STATE_LOCK_OPEN = 1;
    private static final int STATE_FACE_UNLOCK = 2;
    private static final int STATE_FINGERPRINT = 3;
    private static final int STATE_FINGERPRINT_ERROR = 4;

    private int mLastState = 0;
    private boolean mLastDeviceInteractive;
    private boolean mTransientFpError;
    private boolean mDeviceInteractive;
    private boolean mScreenOn;
    private boolean mLastScreenOn;
    private Drawable mUserAvatarIcon;
    private TrustDrawable mTrustDrawable;
    private final UnlockMethodCache mUnlockMethodCache;
    private AccessibilityController mAccessibilityController;
    private boolean mHasFingerPrintIcon;
    private boolean mHasFaceUnlockIcon;
    private boolean mHasFod;
    private int mDensity;

    private final Runnable mDrawOffTimeout = () -> update(true /* forceUpdate */);

    public LockIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTrustDrawable = new TrustDrawable(context);
        setBackground(mTrustDrawable);
        mUnlockMethodCache = UnlockMethodCache.getInstance(context);

        PackageManager packageManager = context.getPackageManager();
        mHasFod = FodUtils.hasFodSupport(context);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isShown()) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTrustDrawable.stop();
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        mUserAvatarIcon = picture;
        update();
    }

    public void setTransientFpError(boolean transientFpError) {
        mTransientFpError = transientFpError;
        update();
    }

    public void setDeviceInteractive(boolean deviceInteractive) {
        mDeviceInteractive = deviceInteractive;
        update();
    }

    public void setScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        update();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final int density = newConfig.densityDpi;
        if (density != mDensity) {
            mDensity = density;
            mTrustDrawable.stop();
            mTrustDrawable = new TrustDrawable(getContext());
            setBackground(mTrustDrawable);
            update();
        }
    }

    public void update() {
        update(false /* force */);
    }

    public void update(boolean force) {
        boolean visible = isShown()
                && KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        if (visible) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
        }
        int state = getState();
        boolean anyFingerprintIcon = state == STATE_FINGERPRINT || state == STATE_FINGERPRINT_ERROR;
        mHasFaceUnlockIcon = state == STATE_FACE_UNLOCK;
        boolean useAdditionalPadding = anyFingerprintIcon;
        boolean trustHidden = anyFingerprintIcon;
        if (state != mLastState || mDeviceInteractive != mLastDeviceInteractive
                || mScreenOn != mLastScreenOn || force) {
            int iconAnimRes =
                getAnimationResForTransition(mLastState, state, mLastDeviceInteractive,
                    mDeviceInteractive, mLastScreenOn, mScreenOn);
            boolean isAnim = iconAnimRes != -1;
            if (iconAnimRes == R.drawable.lockscreen_fingerprint_draw_off_animation) {
                anyFingerprintIcon = true;
                useAdditionalPadding = true;
                trustHidden = true;
            } else if (iconAnimRes == R.drawable.trusted_state_to_error_animation) {
                anyFingerprintIcon = true;
                useAdditionalPadding = false;
                trustHidden = true;
            } else if (iconAnimRes == R.drawable.error_to_trustedstate_animation) {
                anyFingerprintIcon = true;
                useAdditionalPadding = false;
                trustHidden = false;
            }

            Drawable icon;
            if (isAnim) {
                // Load the animation resource.
                icon = mContext.getDrawable(iconAnimRes);
            } else {
                // Load the static icon resource based on the current state.
                icon = getIconForState(state, mScreenOn, mDeviceInteractive);
            }

            final AnimatedVectorDrawable animation = icon instanceof AnimatedVectorDrawable
                    ? (AnimatedVectorDrawable) icon
                    : null;
            int iconHeight = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_height);
            int iconWidth = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_width);
            if (!anyFingerprintIcon && (icon.getIntrinsicHeight() != iconHeight
                    || icon.getIntrinsicWidth() != iconWidth)) {
                icon = new IntrinsicSizeDrawable(icon, iconWidth, iconHeight);
            }
            setPaddingRelative(0, 0, 0, useAdditionalPadding
                    ? getResources().getDimensionPixelSize(
                    R.dimen.fingerprint_icon_additional_padding)
                    : 0);
            setRestingAlpha(
                    anyFingerprintIcon ? 1f : KeyguardAffordanceHelper.SWIPE_RESTING_ALPHA_AMOUNT);
            setImageDrawable(icon, false, false);
            mHasFingerPrintIcon = anyFingerprintIcon;
            if (animation != null && isAnim) {
                animation.forceAnimationOnUI();
                animation.start();
            }

            if (iconAnimRes == R.drawable.lockscreen_fingerprint_draw_off_animation) {
                removeCallbacks(mDrawOffTimeout);
                postDelayed(mDrawOffTimeout, FP_DRAW_OFF_TIMEOUT);
            } else {
                removeCallbacks(mDrawOffTimeout);
            }

            mLastState = state;
            mLastDeviceInteractive = mDeviceInteractive;
            mLastScreenOn = mScreenOn;
        }

        // Hide trust circle when fingerprint is running.
        boolean trustManaged = mUnlockMethodCache.isTrustManaged() && !trustHidden;
        mTrustDrawable.setTrustManaged(trustManaged);
        updateClickability();
    }

    private void updateClickability() {
        if (mAccessibilityController == null) {
            return;
        }
        boolean clickToUnlock = mAccessibilityController.isAccessibilityEnabled();
        boolean clickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !clickToUnlock;
        boolean longClickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !clickToForceLock;
        setClickable(clickToForceLock || clickToUnlock);
        setLongClickable(longClickToForceLock);
        setFocusable(mAccessibilityController.isAccessibilityEnabled());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (mHasFingerPrintIcon) {
            AccessibilityNodeInfo.AccessibilityAction unlock
                    = new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK,
                    getContext().getString(R.string.accessibility_unlock_without_fingerprint));
            info.addAction(unlock);
            info.setHintText(getContext().getString(
                    R.string.accessibility_waiting_for_fingerprint));
        } else if (mHasFaceUnlockIcon){
            //Avoid 'button' to be spoken for scanning face
            info.setClassName(LockIcon.class.getName());
            info.setContentDescription(getContext().getString(
                R.string.accessibility_scanning_face));
        }
    }

    public void setAccessibilityController(AccessibilityController accessibilityController) {
        mAccessibilityController = accessibilityController;
    }

    private Drawable getIconForState(int state, boolean screenOn, boolean deviceInteractive) {
        int iconRes;
        switch (state) {
            case STATE_LOCKED:
                iconRes = R.drawable.ic_lock_24dp;
                break;
            case STATE_LOCK_OPEN:
                if (mUnlockMethodCache.isTrustManaged() && mUnlockMethodCache.isTrusted()
                    && mUserAvatarIcon != null) {
                    return mUserAvatarIcon;
                } else {
                    iconRes = R.drawable.ic_lock_open_24dp;
                }
                break;
            case STATE_FACE_UNLOCK:
                iconRes = R.drawable.ic_face_unlock;
                break;
            case STATE_FINGERPRINT:
                // If screen is off and device asleep, use the draw on animation so the first frame
                // gets drawn.
                iconRes = screenOn && deviceInteractive
                        ? R.drawable.ic_fingerprint
                        : R.drawable.lockscreen_fingerprint_draw_on_animation;
                break;
            case STATE_FINGERPRINT_ERROR:
                iconRes = R.drawable.ic_fingerprint_error;
                break;
            default:
                throw new IllegalArgumentException();
        }

        return mContext.getDrawable(iconRes);
    }

    private int getAnimationResForTransition(int oldState, int newState,
            boolean oldDeviceInteractive, boolean deviceInteractive,
            boolean oldScreenOn, boolean screenOn) {
        if (oldState == STATE_FINGERPRINT && newState == STATE_FINGERPRINT_ERROR) {
            return R.drawable.lockscreen_fingerprint_fp_to_error_state_animation;
        } else if (oldState == STATE_LOCK_OPEN && newState == STATE_FINGERPRINT_ERROR) {
            return R.drawable.trusted_state_to_error_animation;
        } else if (oldState == STATE_FINGERPRINT_ERROR && newState == STATE_LOCK_OPEN) {
            return R.drawable.error_to_trustedstate_animation;
        } else if (oldState == STATE_FINGERPRINT_ERROR && newState == STATE_FINGERPRINT) {
            return R.drawable.lockscreen_fingerprint_error_state_to_fp_animation;
        } else if (oldState == STATE_FINGERPRINT && newState == STATE_LOCK_OPEN
                && !mUnlockMethodCache.isTrusted()) {
            return R.drawable.lockscreen_fingerprint_draw_off_animation;
        } else if (newState == STATE_FINGERPRINT && (!oldScreenOn && screenOn && deviceInteractive
                || screenOn && !oldDeviceInteractive && deviceInteractive)) {
            return R.drawable.lockscreen_fingerprint_draw_on_animation;
        } else {
            return -1;
        }
    }

    private int getState() {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        boolean fingerprintRunning = updateMonitor.isFingerprintDetectionRunning();
        boolean unlockingAllowed = updateMonitor.isUnlockingWithFingerprintAllowed();
        if (mTransientFpError) {
            return mHasFod ? STATE_LOCKED : STATE_FINGERPRINT_ERROR;
        } else if (mUnlockMethodCache.canSkipBouncer()) {
            return STATE_LOCK_OPEN;
        } else if (mUnlockMethodCache.isFaceUnlockRunning()) {
            return STATE_FACE_UNLOCK;
        } else if (fingerprintRunning && unlockingAllowed) {
            return mHasFod ? STATE_LOCKED : STATE_FINGERPRINT;
        } else {
            return STATE_LOCKED;
        }
    }

    /**
     * A wrapper around another Drawable that overrides the intrinsic size.
     */
    private static class IntrinsicSizeDrawable extends InsetDrawable {

        private final int mIntrinsicWidth;
        private final int mIntrinsicHeight;

        public IntrinsicSizeDrawable(Drawable drawable, int intrinsicWidth, int intrinsicHeight) {
            super(drawable, 0);
            mIntrinsicWidth = intrinsicWidth;
            mIntrinsicHeight = intrinsicHeight;
        }

        @Override
        public int getIntrinsicWidth() {
            return mIntrinsicWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mIntrinsicHeight;
        }
    }
}
