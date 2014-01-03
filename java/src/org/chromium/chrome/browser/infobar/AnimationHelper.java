// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.chromium.base.ApiCompatibilityUtils;

import java.util.ArrayList;

/**
 * Sets up animations to move InfoBars around inside of the InfoBarContainer.
 *
 * Animations proceed in several phases:
 * 1) Prep work is done for the InfoBar so that the View being animated in (if it exists) is
 *    properly sized.  This involves adding the View to a FrameLayout with a visibility of
 *    INVISIBLE and triggering a layout.
 *
 * 2) Once the View has an actual size, we compute all of the actions needed for the animation.
 *    We use translations primarily to slide things in and out of the screen as things are shown,
 *    hidden, or resized.
 *
 * 3) The animation is kicked off and the animations run.  During this phase, the View being shown
 *    is added to ContentWrapperView.
 *
 * 4) At the end of the animation, we clean up everything and make sure all the children are in the
 *    right places.
 */
public class AnimationHelper implements ViewTreeObserver.OnGlobalLayoutListener {
    private static final String TAG = "AnimationHelper";

    private static final long ANIMATION_DURATION_MS = 250;

    public static final int ANIMATION_TYPE_SHOW = 0;
    public static final int ANIMATION_TYPE_SWAP = 1;
    public static final int ANIMATION_TYPE_HIDE = 2;
    public static final int ANIMATION_TYPE_BOUNDARY = 3;

    private final InfoBarContainer mContainer;
    private final InfoBar mInfoBar;
    private final ContentWrapperView mTargetWrapperView;
    private final AnimatorSet mAnimatorSet;
    private final int mAnimationType;
    private final View mToShow;

    private boolean mAnimationStarted;

    /**
     * Creates and starts an animation.
     * @param container InfoBarContainer that is having its InfoBars animated.
     * @param target ContentWrapperView that is the focus of the animation and is being resized,
     *               shown, or hidden.
     * @param infoBar InfoBar that goes with the specified ContentWrapperView.
     * @param toShow If non-null, this View will replace whatever child View the ContentWrapperView
     *               is currently displaying.
     * @param aniamtionType Type of animation being performed.
     */
    public AnimationHelper(InfoBarContainer container, ContentWrapperView target, InfoBar infoBar,
            View toShow, int animationType) {
        mContainer = container;
        mInfoBar = infoBar;
        mTargetWrapperView = target;
        mAnimatorSet = new AnimatorSet();
        mAnimationType = animationType;
        mToShow = toShow;
        assert mContainer.indexOfChild(mTargetWrapperView) != -1;
    }

    /**
     * Start the animation.
     */
    public void start() {
        mTargetWrapperView.prepareTransition(mToShow);
        mContainer.prepareTransition(mToShow);

        if (mToShow == null) {
            // We've got a size already; start the animation immediately.
            continueAnimation();
        } else {
            // Wait for the object to be sized.
            mTargetWrapperView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }
    }

    /**
     * @return the InfoBar being animated.
     */
    public InfoBar getInfoBar() {
        return mInfoBar;
    }

    /**
     * @return the ContentWrapperView being animated.
     */
    public ContentWrapperView getTarget() {
        return mTargetWrapperView;
    }

    /**
     * @return the type of animation being performed.
     */
    public int getAnimationType() {
        return mAnimationType;
    }

    /**
     * Catch when the layout occurs, which lets us know when the View has been sized properly.
     */
    @Override
    public void onGlobalLayout() {
        ApiCompatibilityUtils.removeOnGlobalLayoutListener(mTargetWrapperView, this);
        continueAnimation();
    }

    private void continueAnimation() {
        if (mAnimationStarted) return;
        mAnimationStarted = true;

        boolean infoBarsOnTop = mContainer.areInfoBarsOnTop();
        int indexOfWrapperView = mContainer.indexOfChild(mTargetWrapperView);
        assert indexOfWrapperView != -1;

        ArrayList<Animator> animators = new ArrayList<Animator>();
        mTargetWrapperView.getAnimationsForTransition(animators);

        // Determine where the tops of each InfoBar will need to be.
        int heightDifference = mTargetWrapperView.getTransitionHeightDifference();
        int cumulativeTopStart = 0;
        int cumulativeTopEnd = 0;
        int cumulativeEndHeight = 0;
        if (!infoBarsOnTop) {
            if (heightDifference >= 0) {
                // The current container is smaller than the final container, so the current 0
                // coordinate will be >= 0 in the final container.
                cumulativeTopStart = heightDifference;
            } else {
                // The current container is bigger than the final container, so the current 0
                // coordinate will be < 0 in the final container.
                cumulativeTopEnd = -heightDifference;
            }
        }

        for (int i = 0; i < mContainer.getChildCount(); ++i) {
            View view = mContainer.getChildAt(i);

            // At this point, the View being transitioned in shouldn't have been added to the
            // visible container, yet, and shouldn't affect calculations.
            int startHeight = view.getHeight();
            int endHeight = startHeight + (i == indexOfWrapperView ? heightDifference : 0);
            int topStart = cumulativeTopStart;
            int topEnd = cumulativeTopEnd;
            int bottomStart = topStart + startHeight;
            int bottomEnd = topEnd + endHeight;

            if (topStart == topEnd && bottomStart == bottomEnd) {
                // The View needs to stay put.
                view.setTop(topEnd);
                view.setBottom(bottomEnd);
                view.setY(topEnd);
                view.setTranslationY(0);
            } else {
                // A translation is required to move the View into place.
                int translation = heightDifference;
                if (infoBarsOnTop) translation *= -1;

                boolean translateDownward = false;
                if (topStart < topEnd) {
                    translateDownward = infoBarsOnTop;
                } else if (topStart > topEnd) {
                    translateDownward = !infoBarsOnTop;
                } else {
                    translateDownward = bottomEnd > bottomStart;
                }

                PropertyValuesHolder viewTranslation;
                if (translateDownward) {
                    view.setTop(topEnd);
                    view.setBottom(bottomEnd);
                    view.setTranslationY(translation);
                    view.setY(topEnd + translation);
                    viewTranslation =
                            PropertyValuesHolder.ofFloat("translationY", translation, 0.0f);
                } else {
                    viewTranslation =
                            PropertyValuesHolder.ofFloat("translationY", 0.0f, -translation);
                }

                animators.add(ObjectAnimator.ofPropertyValuesHolder(view, viewTranslation));
            }

            // Add heights to the cumulative totals.
            cumulativeTopStart += startHeight;
            cumulativeTopEnd += endHeight;
            cumulativeEndHeight += endHeight;
        }

        // Lock the InfoBarContainer's size at its largest during the animation to avoid
        // clipping issues.
        final int oldContainerTop = mContainer.getTop();
        final int oldContainerBottom = mContainer.getBottom();
        final int newContainerTop;
        final int newContainerBottom;
        if (infoBarsOnTop) {
            newContainerTop = oldContainerTop;
            newContainerBottom = newContainerTop + cumulativeEndHeight;
        } else {
            newContainerBottom = oldContainerBottom;
            newContainerTop = newContainerBottom - cumulativeEndHeight;
        }
        final int biggestContainerTop = Math.min(oldContainerTop, newContainerTop);
        final int biggestContainerBottom = Math.max(oldContainerBottom, newContainerBottom);
        mContainer.setTop(biggestContainerTop);
        mContainer.setBottom(biggestContainerBottom);

        // Set up and run all of the animations.
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mTargetWrapperView.startTransition();
                mContainer.startTransition();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mTargetWrapperView.finishTransition();
                mContainer.finishTransition();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && mToShow != null &&
                        (mAnimationType == ANIMATION_TYPE_SHOW ||
                                mAnimationType == ANIMATION_TYPE_SWAP)) {
                        mToShow.announceForAccessibility(
                                mInfoBar.getMessageText(mContainer.getContext()));
                }
            }
        });

        mAnimatorSet.playTogether(animators);
        mAnimatorSet.setDuration(ANIMATION_DURATION_MS);
        mAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimatorSet.start();
    }
}
