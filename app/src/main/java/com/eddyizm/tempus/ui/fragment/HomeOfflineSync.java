package com.eddyizm.tempus.ui.fragment;

import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeTabMusicBinding;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.service.DownloaderManager;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.DownloadPreferences;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.ExternalAudioReader;
import com.eddyizm.tempus.util.ExternalAudioWriter;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.viewmodel.HomeViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The home screen's offline sync cards: starred tracks, albums and artists that are not on the
 * device yet, with a button to download them. Created and attached by {@link HomeTabMusicFragment}.
 */
@UnstableApi
final class HomeOfflineSync {
    private final Fragment fragment;
    private final HomeViewModel homeViewModel;
    private final FragmentHomeTabMusicBinding bind;
    private final Runnable onReorder;

    HomeOfflineSync(Fragment fragment, HomeViewModel homeViewModel, FragmentHomeTabMusicBinding bind, Runnable onReorder) {
        this.fragment = fragment;
        this.homeViewModel = homeViewModel;
        this.bind = bind;
        this.onReorder = onReorder;
    }

    void attach() {
        initSyncStarredView();
        initSyncStarredAlbumsView();
        initSyncStarredArtistsView();
    }

    private void initSyncStarredView() {
        if (DownloadPreferences.isStarredSyncEnabled()) {
            homeViewModel.getAllStarredTracks().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    if (songs != null && !songs.isEmpty()) {
                        int songsToSyncCount = 0;
                        List<String> toSyncSample = new ArrayList<>();

                        if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                            DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                            for (Child song : songs) {
                                if (!manager.isDownloaded(song.getId())) {
                                    songsToSyncCount++;
                                    if (toSyncSample.size() < 3) {
                                        toSyncSample.add(song.getTitle());
                                    }
                                }
                            }
                        } else {
                            for (Child song : songs) {
                                if (ExternalAudioReader.getUri(song) == null) {
                                    songsToSyncCount++;
                                    if (toSyncSample.size() < 3) {
                                        toSyncSample.add(song.getTitle());
                                    }
                                }
                            }
                        }

                        if (songsToSyncCount > 0) {
                            bind.homeSyncStarredCard.setVisibility(View.VISIBLE);

                            StringBuilder displayText = new StringBuilder();
                            if (!toSyncSample.isEmpty()) {
                                displayText.append(String.join(", ", toSyncSample));
                                if (songsToSyncCount > 3) {
                                    displayText.append("...");
                                }
                            }

                            String countText = fragment.getResources().getQuantityString(
                                    R.plurals.home_sync_starred_songs_count,
                                    songsToSyncCount,
                                    songsToSyncCount
                            );

                            if (displayText.length() > 0) {
                                bind.homeSyncStarredTracksToSync.setText(displayText.toString() + "\n" + countText);
                            } else {
                                bind.homeSyncStarredTracksToSync.setText(countText);
                            }

                            if (fragment.getActivity() != null) {
                                fragment.getActivity().runOnUiThread(() -> onReorder.run());
                            }
                        } else {
                            bind.homeSyncStarredCard.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }

        bind.homeSyncStarredCancel.setOnClickListener(v -> {
            bind.homeSyncStarredCard.setVisibility(View.GONE);
            if (fragment.getActivity() != null) {
                fragment.getActivity().runOnUiThread(() -> onReorder.run());
            }
        });

        bind.homeSyncStarredDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                homeViewModel.getAllStarredTracks().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
                    @Override
                    public void onChanged(List<Child> songs) {
                        if (songs != null && !songs.isEmpty()) {
                            int downloadedCount = 0;

                            if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                                DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                                for (Child song : songs) {
                                    if (!manager.isDownloaded(song.getId())) {
                                        manager.download(MappingUtil.mapDownload(song), new Download(song));
                                        downloadedCount++;
                                    }
                                }
                            } else {
                                for (Child song : songs) {
                                    if (ExternalAudioReader.getUri(song) == null) {
                                        ExternalAudioWriter.downloadToUserDirectory(fragment.requireContext(), song);
                                        downloadedCount++;
                                    }
                                }
                            }

                            if (downloadedCount > 0) {
                                Toast.makeText(fragment.requireContext(),
                                        fragment.getResources().getQuantityString(R.plurals.songs_download_started, downloadedCount, downloadedCount),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        bind.homeSyncStarredCard.setVisibility(View.GONE);
                        if (fragment.getActivity() != null) {
                            fragment.getActivity().runOnUiThread(() -> onReorder.run());
                        }
                    }
                });
            }
        });
    }

    private void initSyncStarredAlbumsView() {

        if (DownloadPreferences.isStarredAlbumsSyncEnabled()) {
            homeViewModel.getStarredAlbums(fragment.getViewLifecycleOwner()).observe(fragment.getViewLifecycleOwner(), new Observer<List<AlbumID3>>() {
                @Override
                public void onChanged(List<AlbumID3> albums) {
                    if (albums != null && !albums.isEmpty()) {
                        checkIfAlbumsNeedSync(albums);
                    }
                }
            });
        }

        bind.homeSyncStarredAlbumsCancel.setOnClickListener(v -> {
            bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
            if (fragment.getActivity() != null) {
                fragment.getActivity().runOnUiThread(() -> onReorder.run());
            }
        });

        bind.homeSyncStarredAlbumsDownload.setOnClickListener(v -> {
            homeViewModel.getAllStarredAlbumSongs().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> allSongs) {
                    if (allSongs != null && !allSongs.isEmpty()) {
                        int songsToDownload = 0;

                        if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                            DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                            for (Child song : allSongs) {
                                if (!manager.isDownloaded(song.getId())) {
                                    manager.download(MappingUtil.mapDownload(song), new Download(song));
                                    songsToDownload++;
                                }
                            }
                        } else {
                            for (Child song : allSongs) {
                                if (ExternalAudioReader.getUri(song) == null) {
                                    ExternalAudioWriter.downloadToUserDirectory(fragment.requireContext(), song);
                                    songsToDownload++;
                                }
                            }
                        }

                        if (songsToDownload > 0) {
                            Toast.makeText(fragment.requireContext(),
                                fragment.getResources().getQuantityString(R.plurals.songs_download_started, songsToDownload, songsToDownload),
                                Toast.LENGTH_SHORT).show();
                        }
                    }

                    bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
                    if (fragment.getActivity() != null) {
                        fragment.getActivity().runOnUiThread(() -> onReorder.run());
                    }
                }
            });
        });
    }

    private void checkIfAlbumsNeedSync(List<AlbumID3> albums) {
        homeViewModel.getAllStarredAlbumSongs().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> allSongs) {
                if (allSongs != null) {
                    int songsToDownload = 0;
                    List<String> albumsNeedingSync = new ArrayList<>();

                    if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                        DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                        
                        for (AlbumID3 album : albums) {
                            boolean albumNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getAlbumId() != null && song.getAlbumId().equals(album.getId()) &&
                                        !manager.isDownloaded(song.getId())) {
                                    songsToDownload++;
                                    albumNeedsSync = true;
                                }
                            }
                            if (albumNeedsSync) {
                                albumsNeedingSync.add(album.getName());
                            }
                        }
                    } else {
                        for (AlbumID3 album : albums) {
                            boolean albumNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getAlbumId() != null && song.getAlbumId().equals(album.getId()) &&
                                        ExternalAudioReader.getUri(song) == null) {
                                    songsToDownload++;
                                    albumNeedsSync = true;
                                }
                            }
                            if (albumNeedsSync) {
                                albumsNeedingSync.add(album.getName());
                            }
                        }
                    }

                    if (songsToDownload > 0) {
                        bind.homeSyncStarredAlbumsCard.setVisibility(View.VISIBLE);
                        
                        StringBuilder displayText = new StringBuilder();
                        List<String> sampleAlbums = new ArrayList<>();
                        
                        for (int i = 0; i < Math.min(albumsNeedingSync.size(), 3); i++) {
                            sampleAlbums.add(albumsNeedingSync.get(i));
                        }
                        
                        if (!sampleAlbums.isEmpty()) {
                            displayText.append(String.join(", ", sampleAlbums));
                            if (albumsNeedingSync.size() > 3) {
                                displayText.append("...");
                            }
                        }
                        
                        String countText = fragment.getResources().getQuantityString(
                            R.plurals.home_sync_starred_albums_count,
                            albumsNeedingSync.size(),
                            albumsNeedingSync.size()
                        );
                        
                        if (displayText.length() > 0) {
                            bind.homeSyncStarredAlbumsToSync.setText(displayText.toString() + "\n" + countText);
                        } else {
                            bind.homeSyncStarredAlbumsToSync.setText(countText);
                        }
                        
                        if (fragment.getActivity() != null) {
                            fragment.getActivity().runOnUiThread(() -> onReorder.run());
                        }
                    } else {
                        bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private void initSyncStarredArtistsView() {
        if (DownloadPreferences.isStarredArtistsSyncEnabled()) {
            homeViewModel.getStarredArtists(fragment.getViewLifecycleOwner()).observe(fragment.getViewLifecycleOwner(), new Observer<List<ArtistID3>>() {
                @Override
                public void onChanged(List<ArtistID3> artists) {
                    if (artists != null && !artists.isEmpty()) {
                        checkIfArtistsNeedSync(artists);
                    }
                }
            });
        }

        bind.homeSyncStarredArtistsCancel.setOnClickListener(v -> {
            bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
            if (fragment.getActivity() != null) {
                fragment.getActivity().runOnUiThread(() -> onReorder.run());
            }
        });

        bind.homeSyncStarredArtistsDownload.setOnClickListener(v -> {
            homeViewModel.getAllStarredArtistSongs().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> allSongs) {
                    if (allSongs != null && !allSongs.isEmpty()) {
                        int songsToDownload = 0;

                        if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                            DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                            for (Child song : allSongs) {
                                if (!manager.isDownloaded(song.getId())) {
                                    manager.download(MappingUtil.mapDownload(song), new Download(song));
                                    songsToDownload++;
                                }
                            }
                        } else {
                            for (Child song : allSongs) {
                                if (ExternalAudioReader.getUri(song) == null) {
                                    ExternalAudioWriter.downloadToUserDirectory(fragment.requireContext(), song);
                                    songsToDownload++;
                                }
                            }
                        }

                        if (songsToDownload > 0) {
                            Toast.makeText(fragment.requireContext(),
                                fragment.getResources().getQuantityString(R.plurals.songs_download_started, songsToDownload, songsToDownload),
                                Toast.LENGTH_SHORT).show();
                        }
                    }

                    bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
                    if (fragment.getActivity() != null) {
                        fragment.getActivity().runOnUiThread(() -> onReorder.run());
                    }
                }
            });
        });
    }

    private void checkIfArtistsNeedSync(List<ArtistID3> artists) {
        homeViewModel.getAllStarredArtistSongs().observe(fragment.getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> allSongs) {
                if (allSongs != null) {
                    int songsToDownload = 0;
                    List<String> artistsNeedingSync = new ArrayList<>();

                    if (DownloadPreferences.getDownloadDirectoryUri() == null) {
                        DownloaderManager manager = DownloadUtil.getDownloadTracker(fragment.requireContext());
                        
                        for (ArtistID3 artist : artists) {
                            boolean artistNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getArtistId() != null && song.getArtistId().equals(artist.getId()) &&
                                        !manager.isDownloaded(song.getId())) {
                                    songsToDownload++;
                                    artistNeedsSync = true;
                                }
                            }
                            if (artistNeedsSync) {
                                artistsNeedingSync.add(artist.getName());
                            }
                        }
                    } else {
                        for (ArtistID3 artist : artists) {
                            boolean artistNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getArtistId() != null && song.getArtistId().equals(artist.getId()) &&
                                        ExternalAudioReader.getUri(song) == null) {
                                    songsToDownload++;
                                    artistNeedsSync = true;
                                }
                            }
                            if (artistNeedsSync) {
                                artistsNeedingSync.add(artist.getName());
                            }
                        }
                    }

                    if (songsToDownload > 0) {
                        bind.homeSyncStarredArtistsCard.setVisibility(View.VISIBLE);
                        
                        StringBuilder displayText = new StringBuilder();
                        List<String> sampleArtists = new ArrayList<>();
                        
                        for (int i = 0; i < Math.min(artistsNeedingSync.size(), 3); i++) {
                            sampleArtists.add(artistsNeedingSync.get(i));
                        }
                        
                        if (!sampleArtists.isEmpty()) {
                            displayText.append(String.join(", ", sampleArtists));
                            if (artistsNeedingSync.size() > 3) {
                                displayText.append("...");
                            }
                        }
                        
                        String countText = fragment.getResources().getQuantityString(
                            R.plurals.home_sync_starred_artists_count,
                            artistsNeedingSync.size(),
                            artistsNeedingSync.size()
                        );
                        
                        if (displayText.length() > 0) {
                            bind.homeSyncStarredArtistsToSync.setText(displayText.toString() + "\n" + countText);
                        } else {
                            bind.homeSyncStarredArtistsToSync.setText(countText);
                        }
                        
                        if (fragment.getActivity() != null) {
                            fragment.getActivity().runOnUiThread(() -> onReorder.run());
                        }
                    } else {
                        bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

}
