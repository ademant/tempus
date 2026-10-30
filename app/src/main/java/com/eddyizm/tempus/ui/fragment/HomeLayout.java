package com.eddyizm.tempus.ui.fragment;

import android.view.View;
import android.view.ViewGroup;

import com.eddyizm.tempus.databinding.FragmentHomeTabMusicBinding;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.function.Supplier;
import androidx.media3.common.util.UnstableApi;

/**
 * Orders the Home tab's sections: which are visible, and in what order they sit in the scrolling
 * container. Owned by {@link HomeTabMusicFragment}, which passes the current binding in through
 * {@code currentBinding} so that a view destroyed before a queued rebuild runs is still seen as gone.
 */
@UnstableApi
final class HomeLayout {
    private final HomeViewModel homeViewModel;
    private final Supplier<FragmentHomeTabMusicBinding> currentBinding;

    HomeLayout(HomeViewModel homeViewModel, Supplier<FragmentHomeTabMusicBinding> currentBinding) {
        this.homeViewModel = homeViewModel;
        this.currentBinding = currentBinding;
    }

    // True while a coalesced home-tree rebuild is already posted, so the burst of reorder()
    // calls that the ~24 async section loads fire at app open collapses into one rebuild/frame.
    private boolean reorderRequested = false;

    public void reorder() {
        // One rebuild per frame at most. Every section load that finishes calls this, and each
        // call used to removeAllViews() + re-add the whole Home tree (~17 nested RecyclerViews).
        // The short-circuit below skips even that when the visible set hasn't changed.
        //
        // Note for the next reader: I first sold this as the fix for the "isn't responding" ANR
        // on app open, and that was wrong. The ANRs kept happening after this landed, and an
        // instrumented build never logged a single reorder() on the reproduce path. The Home tab
        // is drowning in memory pressure from full-resolution cover art, which is a separate
        // problem. This just stops doing the same rebuild many times over - a cheap win, not the
        // ANR fix.
        if (currentBinding.get() == null || homeViewModel.getHomeSectorList() == null) return;
        if (reorderRequested) return;
        reorderRequested = true;
        currentBinding.get().homeLinearLayoutContainer.post(() -> {
            reorderRequested = false;
            reorderNow();
        });
    }

    private void reorderNow() {
        FragmentHomeTabMusicBinding bind = currentBinding.get();
        if (bind == null || homeViewModel.getHomeSectorList() == null) return;

        ArrayList<View> desired = buildDesiredOrder(bind);

        // No change to the visible/ordered set -> nothing to rebuild. Section adapters already
        // update their own rows via setItems(); a reorder is only needed when the set of visible
        // sector views (or their order) changes.
        ViewGroup container = bind.homeLinearLayoutContainer;
        if (container.getChildCount() == desired.size()) {
            boolean unchanged = true;
            for (int i = 0; i < desired.size(); i++) {
                if (container.getChildAt(i) != desired.get(i)) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) return;
        }

        container.removeAllViews();
        for (View v : desired) {
            container.addView(v);
        }
    }

    private ArrayList<View> buildDesiredOrder(FragmentHomeTabMusicBinding bind) {
        ArrayList<View> desired = new ArrayList<>();

        if (bind.homeSyncStarredCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredCard);
        }

        if (bind.homeSyncStarredAlbumsCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredAlbumsCard);
        }

        if (bind.homeSyncStarredArtistsCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredArtistsCard);
        }

        for (HomeSector sector : homeViewModel.getHomeSectorList()) {
            if (!sector.isVisible()) continue;

            switch (sector.getId()) {
                case Constants.HOME_SECTOR_DISCOVERY:
                    desired.add(bind.homeDiscoverSector);
                    break;
                case Constants.HOME_SECTOR_CONTINUE_LISTENING:
                    desired.add(bind.homeContinueListeningSector);
                    break;
                case Constants.HOME_SECTOR_MADE_FOR_YOU:
                    desired.add(bind.homeSimilarTracksSector);
                    break;
                case Constants.HOME_SECTOR_BEST_OF:
                    desired.add(bind.homeBestOfArtistSector);
                    break;
                case Constants.HOME_SECTOR_RADIO_STATION:
                    desired.add(bind.homeRadioArtistSector);
                    break;
                case Constants.HOME_SECTOR_TOP_SONGS:
                    desired.add(bind.homeGridTracksSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_TRACKS:
                    desired.add(bind.starredTracksSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_ALBUMS:
                    desired.add(bind.starredAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_ARTISTS:
                    desired.add(bind.starredArtistsSector);
                    break;
                case Constants.HOME_SECTOR_NEW_RELEASES:
                    desired.add(bind.homeNewReleasesSector);
                    break;
                case Constants.HOME_SECTOR_FLASHBACK:
                    desired.add(bind.homeFlashbackSector);
                    break;
                case Constants.HOME_SECTOR_MOST_PLAYED:
                    desired.add(bind.homeMostPlayedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_LAST_PLAYED:
                    desired.add(bind.homeRecentlyPlayedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_RECENTLY_ADDED:
                    desired.add(bind.homeRecentlyAddedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_PINNED_PLAYLISTS:
                    desired.add(bind.pinnedPlaylistsSector);
                    break;
                case Constants.HOME_SECTOR_SHARED:
                    desired.add(bind.sharesSector);
                    break;
            }
        }

        desired.add(bind.homeSectorRearrangementButton);
        return desired;
    }
}
