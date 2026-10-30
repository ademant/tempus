package com.eddyizm.tempus.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.RecentSearchDao;
import com.eddyizm.tempus.model.RecentSearch;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.subsonic.models.PlaylistWithSongs;
import com.eddyizm.tempus.subsonic.models.SearchResult2;
import com.eddyizm.tempus.subsonic.models.SearchResult3;
import com.eddyizm.tempus.util.IoDispatch;
import com.eddyizm.tempus.util.LibraryPreferences;
import com.eddyizm.tempus.ui.fragment.SearchFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchingRepository {
    private final RecentSearchDao recentSearchDao = AppDatabase.getInstance().recentSearchDao();

    public MutableLiveData<SearchResult2> search2(String query) {
        MutableLiveData<SearchResult2> result = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 0, 20, 0, 20, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            result.setValue(response.body().getSubsonicResponse().getSearchResult2());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return result;
    }

    @UnstableApi
    public MutableLiveData<SearchResult3> search3(SearchFragment sf, String query) {
        MutableLiveData<SearchResult3> result = new MutableLiveData<>();

        IoDispatch.launch(() -> {
            List<Child> allSongs = new ArrayList<>();
            int offset = 0;
            int limit = 1000;
            boolean hasMore = true;

            while (hasMore) {
                try {
                    Response<ApiResponse> response = App.getSubsonicClientInstance(false)
                            .getSearchingClient()
                            .search3(query, limit, offset, 0, 0, 0, 0)
                            .execute();

                    if (response.isSuccessful() && response.body() != null) {
                        SearchResult3 tmp = response.body().getSubsonicResponse().getSearchResult3();
                        if (tmp != null && tmp.getSongs() != null && !tmp.getSongs().isEmpty()) {
                            List<Child> fetchedSongs = tmp.getSongs();
                            allSongs.addAll(fetchedSongs);

                            offset += fetchedSongs.size();
                            hasMore = fetchedSongs.size() == limit;
                        } else {
                            hasMore = false;
                        }
                    } else {
                        hasMore = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    hasMore = false;
                }
            }
            PlaylistWithSongs pws = new PlaylistWithSongs("allsongs", allSongs);
            pws.setSongCount(allSongs.size());
            List<Playlist> lpws = new ArrayList<>();
            lpws.add(pws);
            long duration = 0;
            for (Child song: allSongs) {
                if (song != null && song.getDuration() != null) {
                    duration += song.getDuration();
                }
            }
            pws.setDuration(duration);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (sf.getView() != null && sf.isAdded()) {
                    pws.setName(sf.getView().getContext().getString(R.string.search_all_songs, String.valueOf(allSongs.size())));
                    sf.updateUI(lpws);
                }
            });
        });

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 0, 20, 0, 20, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            result.setValue(response.body().getSubsonicResponse().getSearchResult3());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return result;
    }

    public MutableLiveData<List<String>> getSuggestions(String query) {
        MutableLiveData<List<String>> suggestions = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 5, 0, 5, 0, 5, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<String> newSuggestions = new ArrayList();

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artistID3 : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {
                                    newSuggestions.add(artistID3.getName());
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 albumID3 : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    newSuggestions.add(albumID3.getName());
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                for (Child song : response.body().getSubsonicResponse().getSearchResult3().getSongs()) {
                                    newSuggestions.add(song.getTitle());
                                }
                            }

                            LinkedHashSet<String> hashSet = new LinkedHashSet<>(newSuggestions);
                            ArrayList<String> suggestionsWithoutDuplicates = new ArrayList<>(hashSet);

                            suggestions.setValue(suggestionsWithoutDuplicates);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return suggestions;
    }

    public void insert(RecentSearch recentSearch) {
        IoDispatch.launch(() -> recentSearchDao.insert(recentSearch));
    }

    public void delete(RecentSearch recentSearch) {
        IoDispatch.launch(() -> recentSearchDao.delete(recentSearch));
    }

    public List<String> getRecentSearchSuggestion() {
        return IoDispatch.blockingIo(() -> LibraryPreferences.isSearchSortingChronologicallyEnabled()
                ? recentSearchDao.getRecent()
                : recentSearchDao.getAlpha());
    }
}
