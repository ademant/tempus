package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.SnapHelper;
import androidx.viewpager2.widget.ViewPager2;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeTabMusicBinding;
import com.eddyizm.tempus.helper.recyclerview.CustomLinearSnapHelper;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.repository.QueueRepository;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Share;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.AlbumAdapter;
import com.eddyizm.tempus.ui.adapter.AlbumHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.DiscoverSongAdapter;
import com.eddyizm.tempus.ui.adapter.ContinueListeningAdapter;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.ShareHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.SimilarTrackAdapter;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.YearAdapter;
import com.eddyizm.tempus.ui.dialog.HomeRearrangementDialog;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.ServerPreferences;
import com.eddyizm.tempus.util.UIUtil;
import com.eddyizm.tempus.viewmodel.HomeViewModel;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class HomeTabMusicFragment extends Fragment implements ClickCallback {
    private static final String TAG = "HomeFragment";

    private FragmentHomeTabMusicBinding bind;
    private MainActivity activity;
    private HomeViewModel homeViewModel;
    private MainViewModel mainViewModel;
    private PlaybackViewModel playbackViewModel;

    private DiscoverSongAdapter discoverSongAdapter;
    private SimilarTrackAdapter similarMusicAdapter;
    private ArtistAdapter radioArtistAdapter;
    private ArtistAdapter bestOfArtistAdapter;
    private SongHorizontalAdapter starredSongAdapter;
    private SongHorizontalAdapter topSongAdapter;
    private AlbumHorizontalAdapter starredAlbumAdapter;
    private ArtistHorizontalAdapter starredArtistAdapter;
    private AlbumAdapter recentlyAddedAlbumAdapter;
    private AlbumAdapter recentlyPlayedAlbumAdapter;
    private AlbumAdapter mostPlayedAlbumAdapter;
    private AlbumHorizontalAdapter newReleasesAlbumAdapter;
    private YearAdapter yearAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;
    private ShareHorizontalAdapter shareHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private ContinueListeningAdapter continueListeningAdapter;
    private Observer<List<Child>> bestOfObserver = null;
    private HomeOfflineSync offlineSync;
    private HomeLayout homeLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentHomeTabMusicBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        homeLayout = new HomeLayout(homeViewModel, () -> bind);

        homeViewModel.clearCacheIfMusicFolderChanged();

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        offlineSync = new HomeOfflineSync(this, homeViewModel, bind, this::reorder);
        offlineSync.attach();
        initDiscoverSongSlideView();
        initContinueListening();
        initSimilarSongView();
        initArtistRadio();
        initArtistBestOf();
        initStarredTracksView();
        initStarredAlbumsView();
        initStarredArtistsView();
        initMostPlayedAlbumView();
        initRecentPlayedAlbumView();
        initNewReleasesView();
        initYearSongView();
        initRecentAddedAlbumView();
        initTopSongsView();
        initPinnedPlaylistsView();
        initSharesView();
        initHomeReorganizer();
        initMusicFolderChangeListener();

        reorder();
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();

        if (continueListeningAdapter != null) {
            continueListeningAdapter.setMediaBrowserFuture(mediaBrowserListenableFuture);
        }

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observeStarredSongsPlayback();
        observeTopSongsPlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSharesView();
        refreshPlaylistView();
        if (topSongAdapter != null) setTopSongsMediaBrowserListenableFuture();
        if (starredSongAdapter != null) setStarredSongsMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        bind.discoveryTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshDiscoverySongSample(getViewLifecycleOwner());
            return true;
        });

        bind.discoveryTextViewClickable.setOnClickListener(v -> {
            homeViewModel.getRandomShuffleSample().observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                if (!songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                }
            });
        });

        bind.similarTracksTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshSimilarSongSample(getViewLifecycleOwner());
            return true;
        });

        bind.radioArtistTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshRadioArtistSample(getViewLifecycleOwner());
            return true;
        });

        bind.bestOfArtistTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshBestOfArtist(getViewLifecycleOwner());
            return true;
        });

        bind.starredTracksTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_STARRED, Constants.MEDIA_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_songListPageFragment, bundle);
        });

        bind.starredAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_STARRED, Constants.ALBUM_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredArtistsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARTIST_STARRED, Constants.ARTIST_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_artistListPageFragment, bundle);
        });

        bind.recentlyAddedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_ADDED, Constants.ALBUM_RECENTLY_ADDED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_homeFragment_to_playlistCatalogueFragment, bundle);
        });

        bind.recentlyPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_PLAYED, Constants.ALBUM_RECENTLY_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.mostPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_MOST_PLAYED, Constants.ALBUM_MOST_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredTracksTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredTracks(getViewLifecycleOwner());
            return true;
        });

        bind.starredAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.starredArtistsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredArtists(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshRecentlyPlayedAlbumList(getViewLifecycleOwner());
            return true;
        });

        bind.mostPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshMostPlayedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyAddedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshMostRecentlyAddedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.sharesTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshShares(getViewLifecycleOwner());
            return true;
        });

        bind.gridTracksPreTextView.setOnClickListener(view -> showPopupMenu(view, R.menu.filter_top_songs_popup_menu));
    }

    private void initDiscoverSongSlideView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_DISCOVERY)) return;

        bind.discoverSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.discoverSongRecyclerView.setHasFixedSize(true);

        discoverSongAdapter = new DiscoverSongAdapter(this);
        bind.discoverSongRecyclerView.setAdapter(discoverSongAdapter);

        homeViewModel.getDiscoverSongSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeDiscoverSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeDiscoverSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                discoverSongAdapter.setItems(songs);
            }
        });
    }

    private void initContinueListening() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_CONTINUE_LISTENING)) return;

        bind.continueListeningRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.continueListeningRecyclerView.setHasFixedSize(true);

        continueListeningAdapter = new ContinueListeningAdapter(mediaBrowserListenableFuture);
        bind.continueListeningRecyclerView.setAdapter(continueListeningAdapter);

        List<Preferences.ResumePoint> resumePoints = Preferences.getContinueListening();
        if (resumePoints.isEmpty()) {
            bind.homeContinueListeningSector.setVisibility(View.GONE);
        } else {
            bind.homeContinueListeningSector.setVisibility(View.VISIBLE);
            continueListeningAdapter.setItems(resumePoints);
        }

        // Sync from server bookmarks (async) so cross-device resume points appear too, then refresh.
        new QueueRepository().syncBookmarksFromServer(() -> {
            if (continueListeningAdapter == null || bind == null) return;
            List<Preferences.ResumePoint> synced = Preferences.getContinueListening();
            if (synced.isEmpty()) {
                bind.homeContinueListeningSector.setVisibility(View.GONE);
            } else {
                bind.homeContinueListeningSector.setVisibility(View.VISIBLE);
                continueListeningAdapter.setItems(synced);
            }
        });
    }

    private void initSimilarSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MADE_FOR_YOU)) return;

        bind.similarTracksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.similarTracksRecyclerView.setHasFixedSize(true);

        similarMusicAdapter = new SimilarTrackAdapter(this);
        bind.similarTracksRecyclerView.setAdapter(similarMusicAdapter);
        homeViewModel.getStarredTracksSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeSimilarTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeSimilarTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                similarMusicAdapter.setItems(songs);
            }
        });

        CustomLinearSnapHelper similarSongSnapHelper = new CustomLinearSnapHelper();
        similarSongSnapHelper.attachToRecyclerView(bind.similarTracksRecyclerView);
    }

    private void initArtistBestOf() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_BEST_OF)) return;

        bind.bestOfArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.bestOfArtistRecyclerView.setHasFixedSize(true);

        bestOfArtistAdapter = new ArtistAdapter(this, false, true);
        bind.bestOfArtistRecyclerView.setAdapter(bestOfArtistAdapter);
        homeViewModel.getBestOfArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeBestOfArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeBestOfArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                bestOfArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistBestOfSnapHelper = new CustomLinearSnapHelper();
        artistBestOfSnapHelper.attachToRecyclerView(bind.bestOfArtistRecyclerView);
    }

    private void initArtistRadio() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RADIO_STATION)) return;

        bind.radioArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.radioArtistRecyclerView.setHasFixedSize(true);

        radioArtistAdapter = new ArtistAdapter(this, true, false);
        bind.radioArtistRecyclerView.setAdapter(radioArtistAdapter);
        homeViewModel.getStarredArtistsSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeRadioArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRadioArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterRadioArtistDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                radioArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistRadioSnapHelper = new CustomLinearSnapHelper();
        artistRadioSnapHelper.attachToRecyclerView(bind.radioArtistRecyclerView);
    }

    private void initTopSongsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_TOP_SONGS)) return;

        bind.topSongsRecyclerView.setHasFixedSize(true);

        topSongAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.topSongsRecyclerView.setAdapter(topSongAdapter);
        setTopSongsMediaBrowserListenableFuture();
        reapplyTopSongsPlayback();
        homeViewModel.getChronologySample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), chronologies -> {
            if (chronologies == null || chronologies.isEmpty()) {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.GONE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.GONE);
            } else {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.VISIBLE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.VISIBLE);
                if (bind != null)
                    bind.topSongsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(chronologies.size(), 5), GridLayoutManager.HORIZONTAL, false));

                List<Child> topSongs = chronologies.stream()
                        .map(cronologia -> (Child) cronologia)
                        .collect(Collectors.toList());

                topSongAdapter.setItems(topSongs);
                reapplyTopSongsPlayback();
            }
        });

        SnapHelper topTrackSnapHelper = new PagerSnapHelper();
        topTrackSnapHelper.attachToRecyclerView(bind.topSongsRecyclerView);

        bind.topSongsRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initStarredTracksView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_TRACKS)) return;

        bind.starredTracksRecyclerView.setHasFixedSize(true);

        starredSongAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.starredTracksRecyclerView.setAdapter(starredSongAdapter);
        setStarredSongsMediaBrowserListenableFuture();
        reapplyStarredSongsPlayback();
        homeViewModel.getStarredTracks(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            if (songs == null) {
                if (bind != null) bind.starredTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredTracksRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(songs.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredSongAdapter.setItems(songs);
                reapplyStarredSongsPlayback();
            }
        });

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.starredTracksRecyclerView);

        bind.starredTracksRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initStarredAlbumsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ALBUMS)) return;

        bind.starredAlbumsRecyclerView.setHasFixedSize(true);

        starredAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.starredAlbumsRecyclerView.setAdapter(starredAlbumAdapter);
        homeViewModel.getStarredAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.starredAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredAlbumsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(albums.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper starredAlbumSnapHelper = new PagerSnapHelper();
        starredAlbumSnapHelper.attachToRecyclerView(bind.starredAlbumsRecyclerView);

        bind.starredAlbumsRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initStarredArtistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ARTISTS)) return;

        bind.starredArtistsRecyclerView.setHasFixedSize(true);

        starredArtistAdapter = new ArtistHorizontalAdapter(this);
        bind.starredArtistsRecyclerView.setAdapter(starredArtistAdapter);
        homeViewModel.getStarredArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.starredArtistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredArtistsSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterFavoritesDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredArtistsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(artists.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredArtistAdapter.setItems(artists);
            }
        });

        SnapHelper starredArtistSnapHelper = new PagerSnapHelper();
        starredArtistSnapHelper.attachToRecyclerView(bind.starredArtistsRecyclerView);

        bind.starredArtistsRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initNewReleasesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_NEW_RELEASES)) return;

        bind.newReleasesRecyclerView.setHasFixedSize(true);

        newReleasesAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.newReleasesRecyclerView.setAdapter(newReleasesAlbumAdapter);
        homeViewModel.getRecentlyReleasedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeNewReleasesSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeNewReleasesSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.newReleasesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(albums.size(), 5), GridLayoutManager.HORIZONTAL, false));

                newReleasesAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper newReleasesSnapHelper = new PagerSnapHelper();
        newReleasesSnapHelper.attachToRecyclerView(bind.newReleasesRecyclerView);

        bind.newReleasesRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initYearSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_FLASHBACK)) return;

        bind.yearsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.yearsRecyclerView.setHasFixedSize(true);

        yearAdapter = new YearAdapter(this);
        bind.yearsRecyclerView.setAdapter(yearAdapter);
        homeViewModel.getYearList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), years -> {
            if (years == null) {
                if (bind != null) bind.homeFlashbackSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeFlashbackSector.setVisibility(!years.isEmpty() ? View.VISIBLE : View.GONE);

                yearAdapter.setItems(years);
            }
        });

        CustomLinearSnapHelper yearSnapHelper = new CustomLinearSnapHelper();
        yearSnapHelper.attachToRecyclerView(bind.yearsRecyclerView);
    }

    private void initMostPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MOST_PLAYED)) return;

        bind.mostPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.mostPlayedAlbumsRecyclerView.setHasFixedSize(true);

        mostPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.mostPlayedAlbumsRecyclerView.setAdapter(mostPlayedAlbumAdapter);
        homeViewModel.getMostPlayedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeMostPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeMostPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                mostPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper mostPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        mostPlayedAlbumSnapHelper.attachToRecyclerView(bind.mostPlayedAlbumsRecyclerView);
    }

    private void initRecentPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_LAST_PLAYED)) return;

        bind.recentlyPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyPlayedAlbumsRecyclerView.setHasFixedSize(true);

        recentlyPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyPlayedAlbumsRecyclerView.setAdapter(recentlyPlayedAlbumAdapter);
        homeViewModel.getRecentlyPlayedAlbumList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentPlayedAlbumSnapHelper.attachToRecyclerView(bind.recentlyPlayedAlbumsRecyclerView);
    }

    private void initRecentAddedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RECENTLY_ADDED)) return;

        bind.recentlyAddedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyAddedAlbumsRecyclerView.setHasFixedSize(true);

        recentlyAddedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyAddedAlbumsRecyclerView.setAdapter(recentlyAddedAlbumAdapter);
        homeViewModel.getMostRecentlyAddedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyAddedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyAddedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyAddedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentAddedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentAddedAlbumSnapHelper.attachToRecyclerView(bind.recentlyAddedAlbumsRecyclerView);
    }

    private void initPinnedPlaylistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_PINNED_PLAYLISTS)) return;

        bind.pinnedPlaylistsRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.pinnedPlaylistsRecyclerView.setAdapter(playlistHorizontalAdapter);
        homeViewModel.getPinnedPlaylists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.pinnedPlaylistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.pinnedPlaylistsSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.pinnedPlaylistsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(playlists.size(), 5), GridLayoutManager.HORIZONTAL, false));

                playlistHorizontalAdapter.setItems(playlists);
            }
        });

        SnapHelper pinnedPlaylistsSnapHelper = new PagerSnapHelper();
        pinnedPlaylistsSnapHelper.attachToRecyclerView(bind.pinnedPlaylistsRecyclerView);

        bind.pinnedPlaylistsRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initSharesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_SHARED)) return;

        bind.sharesRecyclerView.setHasFixedSize(true);

        shareHorizontalAdapter = new ShareHorizontalAdapter(this);
        bind.sharesRecyclerView.setAdapter(shareHorizontalAdapter);
        if (ServerPreferences.isSharingEnabled()) {
            homeViewModel.getShares(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), shares -> {
                if (shares == null) {
                    if (bind != null) bind.sharesSector.setVisibility(View.GONE);
                } else {
                    if (bind != null)
                        bind.sharesSector.setVisibility(!shares.isEmpty() ? View.VISIBLE : View.GONE);
                    if (bind != null)
                        bind.sharesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(shares.size(), 10), GridLayoutManager.HORIZONTAL, false));

                    shareHorizontalAdapter.setItems(shares);
                }
            });
        }

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.sharesRecyclerView);

        bind.sharesRecyclerView.addItemDecoration(
                HomeCarousels.dots(requireContext())
        );
    }

    private void initMusicFolderChangeListener() {
        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId ->
                homeViewModel.reloadIfMusicFolderChanged(getViewLifecycleOwner()));
    }

    private void initHomeReorganizer() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = () -> {
            if (bind != null) bind.homeSectorRearrangementButton.setVisibility(View.VISIBLE);
        };
        handler.postDelayed(runnable, 5000);

        bind.homeSectorRearrangementButton.setOnClickListener(v -> {
            HomeRearrangementDialog dialog = new HomeRearrangementDialog();
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        });
    }

    private void refreshSharesView() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = () -> {
            if (getView() != null && bind != null && ServerPreferences.isSharingEnabled()) {
                homeViewModel.refreshShares(getViewLifecycleOwner());
            }
        };
        handler.postDelayed(runnable, 100);
    }

    private void setSlideViewOffset(ViewPager2 viewPager, float pageOffset, float pageMargin) {
        viewPager.setPageTransformer((page, position) -> {
            float myOffset = position * -(2 * pageOffset + pageMargin);
            if (viewPager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL) {
                if (ViewCompat.getLayoutDirection(viewPager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.setTranslationX(-myOffset);
                } else {
                    page.setTranslationX(myOffset);
                }
            } else {
                page.setTranslationY(myOffset);
            }
        });
    }

    public void reorder() {
        if (homeLayout == null) return;
        homeLayout.reorder();
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_last_week_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 0);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_week));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_month_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 1);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_month));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_year_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 2);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_year));
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && homeViewModel != null)
                homeViewModel.refreshPinnedPlaylists();
        };

        handler.postDelayed(runnable, 100);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    public void onMediaClick(Bundle bundle) {
        if (bundle.containsKey(Constants.MEDIA_MIX)) {
            Child track = bundle.getParcelable(Constants.TRACK_OBJECT);
            activity.setBottomSheetInPeek(true);

            if (mediaBrowserListenableFuture != null) {
                final boolean[] playbackStarted = {false};
                Toast.makeText(requireContext(), R.string.bottom_sheet_generating_instant_mix, Toast.LENGTH_SHORT).show();
                homeViewModel.getMediaInstantMix(getViewLifecycleOwner(), track)
                        .observe(getViewLifecycleOwner(), songs -> {
                            if (playbackStarted[0] || songs == null || songs.isEmpty()) return;

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (playbackStarted[0]) return;

                                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                                playbackStarted[0] = true;
                            }, 300);
                        });
            }
        } else if (bundle.containsKey(Constants.MEDIA_CHRONOLOGY)) {
            List<Child> media = bundle.getParcelableArrayList(Constants.TRACKS_OBJECT);
            MediaManager.startQueue(mediaBrowserListenableFuture, media, bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        } else {
            MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        }
        topSongAdapter.notifyDataSetChanged();
        starredSongAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        if (bundle.containsKey(Constants.MEDIA_MIX) && bundle.getBoolean(Constants.MEDIA_MIX)) {
            Snackbar.make(activity.bind.playerBottomSheet, R.string.artist_adapter_radio_station_starting, Snackbar.LENGTH_LONG)
                    .setAnchorView(activity.getSnackbarAnchor())
                    .show();

            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistInstantMix(getViewLifecycleOwner(), bundle.getParcelable(Constants.ARTIST_OBJECT)).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);

                    if (songs != null && !songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else if (bundle.containsKey(Constants.MEDIA_BEST_OF) && bundle.getBoolean(Constants.MEDIA_BEST_OF)) {
            ArtistID3 artist = bundle.getParcelable(Constants.ARTIST_OBJECT);
            if (artist == null) return;
            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistBestOf(artist).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);
                    if (songs != null && !songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else {
            Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
        }
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onYearClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onShareClick(Bundle bundle) {
        Share share = bundle.getParcelable(Constants.SHARE_OBJECT);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(share.getUrl())).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }

    @Override
    public void onShareLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.shareBottomSheetDialog, bundle);
    }

    private void observeStarredSongsPlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (starredSongAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                starredSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (starredSongAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                starredSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void observeTopSongsPlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (topSongAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                topSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (topSongAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                topSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyStarredSongsPlayback() {
        if (starredSongAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            starredSongAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void reapplyTopSongsPlayback() {
        if (topSongAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            topSongAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setTopSongsMediaBrowserListenableFuture() {
        topSongAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void setStarredSongsMediaBrowserListenableFuture() {
        starredSongAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }
}
