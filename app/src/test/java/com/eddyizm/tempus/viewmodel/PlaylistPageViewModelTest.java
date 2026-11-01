package com.eddyizm.tempus.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pins down what PlaylistPageViewModel does today, so its move to Kotlin and Flow can be checked
 * against it. The repository is mocked at construction, so no database is opened.
 */
public class PlaylistPageViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private MutableLiveData<Boolean> updateTrigger;
    private MockedConstruction<PlaylistRepository> repositories;
    private PlaylistRepository repository;
    private PlaylistPageViewModel viewModel;

    @Before
    public void setUp() {
        updateTrigger = new MutableLiveData<>();
        repositories = mockConstruction(PlaylistRepository.class, (mock, context) ->
                when(mock.getPlaylistUpdateTrigger()).thenReturn(updateTrigger));
        viewModel = new PlaylistPageViewModel(mock(Application.class));
        repository = repositories.constructed().get(0);
    }

    @After
    public void tearDown() {
        repositories.close();
    }

    private static Playlist playlist(String id) {
        return new Playlist(id, "Name " + id, 0L, null);
    }

    private static List<Child> songs(String... ids) {
        List<Child> songs = new ArrayList<>();
        for (String id : ids) songs.add(new Child(id));
        return songs;
    }

    private static List<String> idsOf(List<Child> songs) {
        List<String> ids = new ArrayList<>();
        for (Child song : songs) ids.add(song.getId());
        return ids;
    }

    /** Opens the page on playlist id with a remote read that the test resolves by hand. */
    private MutableLiveData<List<Child>> openWithRemote(String id, String... initialSongs) {
        MutableLiveData<List<Child>> remote = new MutableLiveData<>();
        when(repository.getPlaylistSongs(eq(id), eq(true), any())).thenReturn(remote);
        viewModel.setPlaylist(playlist(id));
        viewModel.getPlaylistSongLiveList();
        remote.setValue(songs(initialSongs));
        return remote;
    }

    @Test
    public void openingAPlaylistFetchesItsSongsAndPublishesThem() {
        MutableLiveData<List<Child>> remote = new MutableLiveData<>();
        when(repository.getPlaylistSongs(eq("p1"), eq(true), any())).thenReturn(remote);
        viewModel.setPlaylist(playlist("p1"));

        LiveData<List<Child>> live = viewModel.getPlaylistSongLiveList();
        assertNull(live.getValue());

        remote.setValue(songs("a", "b"));
        assertEquals(Arrays.asList("a", "b"), idsOf(viewModel.songs().getValue()));
    }

    @Test
    public void answerForAPlaylistThePageHasLeftIsDropped() {
        MutableLiveData<List<Child>> remote = new MutableLiveData<>();
        when(repository.getPlaylistSongs(eq("p1"), eq(true), any())).thenReturn(remote);
        viewModel.setPlaylist(playlist("p1"));
        viewModel.getPlaylistSongLiveList();

        viewModel.setPlaylist(playlist("p2"));
        remote.setValue(songs("a"));

        assertNull(viewModel.songs().getValue());
    }

    @Test
    public void updateTriggerRefetchesTheOpenPlaylist() {
        viewModel.setPlaylist(playlist("p1"));
        when(repository.getPlaylistSongs(eq("p1"), eq(true), any())).thenReturn(new MutableLiveData<>());

        updateTrigger.setValue(true);

        verify(repository, times(1)).getPlaylistSongs(eq("p1"), eq(true), any());
    }

    @Test
    public void updateTriggerWithoutAPlaylistDoesNothing() {
        updateTrigger.setValue(true);

        verify(repository, never()).getPlaylistSongs(any(), anyBoolean(), any());
    }

    @Test
    public void removeSongShowsTheShorterListAtOnce() {
        openWithRemote("p1", "a", "b", "c");

        viewModel.removeSong("p1", 1, mock(PlaylistRepository.AddToPlaylistCallback.class));

        assertEquals(Arrays.asList("a", "c"), idsOf(viewModel.songs().getValue()));
        assertTrue(viewModel.isWriting());
    }

    @Test
    public void removeSongSuccessKeepsTheShorterListAndReleasesTheWrite() {
        openWithRemote("p1", "a", "b", "c");
        PlaylistRepository.AddToPlaylistCallback callback = mock(PlaylistRepository.AddToPlaylistCallback.class);

        viewModel.removeSong("p1", 1, callback);
        ArgumentCaptor<PlaylistRepository.AddToPlaylistCallback> server = ArgumentCaptor.forClass(PlaylistRepository.AddToPlaylistCallback.class);
        verify(repository).removeSongFromPlaylist(eq("p1"), eq(1), server.capture());
        server.getValue().onSuccess();

        assertEquals(Arrays.asList("a", "c"), idsOf(viewModel.songs().getValue()));
        assertFalse(viewModel.isWriting());
        verify(callback).onSuccess();
    }

    @Test
    public void removeSongFailurePutsTheRowBackAndAsksTheServer() {
        openWithRemote("p1", "a", "b", "c");
        PlaylistRepository.AddToPlaylistCallback callback = mock(PlaylistRepository.AddToPlaylistCallback.class);
        MutableLiveData<List<Child>> reconcile = new MutableLiveData<>();
        when(repository.getPlaylistSongs(eq("p1"), eq(false), any())).thenReturn(reconcile);

        viewModel.removeSong("p1", 1, callback);
        ArgumentCaptor<PlaylistRepository.AddToPlaylistCallback> server = ArgumentCaptor.forClass(PlaylistRepository.AddToPlaylistCallback.class);
        verify(repository).removeSongFromPlaylist(eq("p1"), eq(1), server.capture());
        server.getValue().onFailure();

        assertEquals(Arrays.asList("a", "b", "c"), idsOf(viewModel.songs().getValue()));
        verify(repository).getPlaylistSongs(eq("p1"), eq(false), any());
        verify(callback).onFailure();
        assertTrue("the write stays held until the server's list lands", viewModel.isWriting());

        reconcile.setValue(songs("a", "c"));
        assertEquals(Arrays.asList("a", "c"), idsOf(viewModel.songs().getValue()));
        assertFalse(viewModel.isWriting());
    }

    @Test
    public void secondRemoveWhileAWriteIsOutIsRefused() {
        openWithRemote("p1", "a", "b", "c");
        viewModel.removeSong("p1", 0, mock(PlaylistRepository.AddToPlaylistCallback.class));
        PlaylistRepository.AddToPlaylistCallback refused = mock(PlaylistRepository.AddToPlaylistCallback.class);

        viewModel.removeSong("p1", 0, refused);

        verify(repository, times(1)).removeSongFromPlaylist(any(), anyInt(), any());
        verify(refused).onFailure();
    }

    @Test
    public void restoreInsertsTheSongAndWritesTheWholeList() {
        openWithRemote("p1", "a", "b", "c");
        MutableLiveData<List<Child>> current = new MutableLiveData<>();
        when(repository.getPlaylistSongs(eq("p1"), eq(false), any())).thenReturn(current);
        PlaylistRepository.PlaylistActionCallback callback = mock(PlaylistRepository.PlaylistActionCallback.class);

        viewModel.restoreSong("p1", new Child("x"), 1, callback);
        current.setValue(songs("a", "c"));

        ArrayList<String> written = new ArrayList<>(Arrays.asList("a", "x", "c"));
        ArgumentCaptor<PlaylistRepository.PlaylistActionCallback> server = ArgumentCaptor.forClass(PlaylistRepository.PlaylistActionCallback.class);
        verify(repository).createPlaylist(eq("p1"), isNull(), eq(written), server.capture());
        server.getValue().onSuccess();

        assertEquals(Arrays.asList("a", "x", "c"), idsOf(viewModel.songs().getValue()));
        assertFalse(viewModel.isWriting());
        verify(callback).onSuccess();
    }

    @Test
    public void missingPlaylistRaisesTheEventOnceUntilTheNextOpen() {
        ArgumentCaptor<Runnable> onMissing = ArgumentCaptor.forClass(Runnable.class);
        when(repository.getPlaylistSongs(eq("p1"), eq(true), onMissing.capture())).thenReturn(new MutableLiveData<>());
        viewModel.setPlaylist(playlist("p1"));
        viewModel.getPlaylistSongLiveList();

        onMissing.getValue().run();
        assertEquals(Boolean.TRUE, viewModel.missingEvent().getValue());

        viewModel.clearPlaylistMissingEvent();
        onMissing.getValue().run();
        assertEquals(Boolean.FALSE, viewModel.missingEvent().getValue());
    }

    @Test
    public void openingADifferentPlaylistClearsTheSongs() {
        openWithRemote("p1", "a", "b");

        viewModel.setPlaylist(playlist("p2"));

        assertNull(viewModel.songs().getValue());
    }

    @Test
    public void pinWritesThePlaylistAndUnpinRemovesIt() {
        viewModel.setPlaylist(playlist("p1"));

        viewModel.setPinned(true);
        verify(repository).insertIfAbsent(viewModel.getPlaylist());
        verify(repository).pin("p1");

        viewModel.setPinned(false);
        verify(repository).unpin("p1");
    }

    @Test
    public void clearedViewModelStopsListeningForUpdates() {
        viewModel.setPlaylist(playlist("p1"));

        viewModel.onCleared();
        updateTrigger.setValue(true);

        verify(repository, never()).getPlaylistSongs(any(), anyBoolean(), any());
    }
}
