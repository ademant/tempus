package com.eddyizm.tempus.ui.fragment;

import android.content.Context;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.helper.recyclerview.DotsIndicatorDecoration;

/** Setup shared by the home screen's horizontal carousels. */
final class HomeCarousels {
    private HomeCarousels() {}

    /** The page dots shown under a carousel. */
    static DotsIndicatorDecoration dots(Context context) {
        return new DotsIndicatorDecoration(
                context.getResources().getDimensionPixelSize(R.dimen.radius),
                context.getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                context.getResources().getDimensionPixelSize(R.dimen.dots_height),
                context.getResources().getColor(R.color.titleTextColor, null),
                context.getResources().getColor(R.color.titleTextColor, null));
    }
}
