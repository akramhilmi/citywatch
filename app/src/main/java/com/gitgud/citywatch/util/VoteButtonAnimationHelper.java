package com.gitgud.citywatch.util;

import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;

import com.google.android.material.button.MaterialButton;

/**
 * Helper class to apply click animations to vote buttons
 * Provides scale and bounce animation effects for both ImageButton and MaterialButton
 */
public class VoteButtonAnimationHelper {

    /**
     * Animate vote button with scale effect on click
     * Creates a bouncy scale animation: scale up then back to normal
     * Works with ImageButton
     */
    public static void animateVoteButton(ImageButton button) {
        // Scale up animation (1.0x -> 1.2x)
        ScaleAnimation scaleUp = new ScaleAnimation(
                1f, 1.2f,
                1f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleUp.setDuration(150);
        scaleUp.setFillAfter(false);

        // Scale down animation (1.2x -> 1.0x)
        ScaleAnimation scaleDown = new ScaleAnimation(
                1.2f, 1f,
                1.2f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleDown.setDuration(150);
        scaleDown.setStartOffset(150);
        scaleDown.setFillAfter(true);

        // Set animation listener to chain animations
        scaleUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                button.startAnimation(scaleDown);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        button.startAnimation(scaleUp);
    }

    /**
     * Animate MaterialButton with scale effect on click
     * Creates a bouncy scale animation: scale up then back to normal
     * Works with MaterialButton
     */
    public static void animateVoteButton(MaterialButton button) {
        // Scale up animation (1.0x -> 1.2x)
        ScaleAnimation scaleUp = new ScaleAnimation(
                1f, 1.2f,
                1f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleUp.setDuration(150);
        scaleUp.setFillAfter(false);

        // Scale down animation (1.2x -> 1.0x)
        ScaleAnimation scaleDown = new ScaleAnimation(
                1.2f, 1f,
                1.2f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleDown.setDuration(150);
        scaleDown.setStartOffset(150);
        scaleDown.setFillAfter(true);

        // Set animation listener to chain animations
        scaleUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                button.startAnimation(scaleDown);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        button.startAnimation(scaleUp);
    }

    /**
     * Animate vote button with shorter quick tap effect (ImageButton)
     */
    public static void animateVoteButtonQuick(ImageButton button) {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.15f,
                1f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(200);
        scale.setFillAfter(false);

        button.startAnimation(scale);
    }

    /**
     * Animate vote button with shorter quick tap effect (MaterialButton)
     */
    public static void animateVoteButtonQuick(MaterialButton button) {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.15f,
                1f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(200);
        scale.setFillAfter(false);

        button.startAnimation(scale);
    }
}
