package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.LyricsDao;
import com.eddyizm.tempus.model.LyricsCache;
import com.eddyizm.tempus.util.IoDispatch;

public class LyricsRepository {
    private final LyricsDao lyricsDao = AppDatabase.getInstance().lyricsDao();

    public LyricsCache getLyrics(String songId) {
        return IoDispatch.blockingIo(() -> lyricsDao.getOne(songId));
    }

    public LiveData<LyricsCache> observeLyrics(String songId) {
        return lyricsDao.observeOne(songId);
    }

    public void insert(LyricsCache lyricsCache) {
        IoDispatch.launch(() -> lyricsDao.insert(lyricsCache));
    }

    public void delete(String songId) {
        IoDispatch.launch(() -> lyricsDao.delete(songId));
    }
}
