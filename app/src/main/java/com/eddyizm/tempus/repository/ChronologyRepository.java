package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.ChronologyDao;
import com.eddyizm.tempus.model.Chronology;
import com.eddyizm.tempus.util.IoDispatch;

import java.util.Calendar;
import java.util.List;

public class ChronologyRepository {
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    public LiveData<List<Chronology>> getChronology(String server, long start, long end) {
        return chronologyDao.getAllFrom(start, end, server);
    }

    public void insert(Chronology item) {
        IoDispatch.launch(() -> chronologyDao.insert(item));
    }
}
