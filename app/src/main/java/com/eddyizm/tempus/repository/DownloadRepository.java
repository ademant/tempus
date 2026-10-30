package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.DownloadDao;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.util.IoDispatch;

import java.util.List;

public class DownloadRepository {
    private final DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

    public LiveData<List<Download>> getLiveDownload() {
        return downloadDao.getAll();
    }

    public List<Download> getAllDownloads() {
        return IoDispatch.blockingIo(downloadDao::getAllSync);
    }

    public Download getDownload(String id) {
        return IoDispatch.blockingIo(() -> downloadDao.getOne(id));
    }

    public List<Download> getFailedDownloads() {
        return IoDispatch.blockingIo(downloadDao::getFailedSync);
    }

    // Writes go through IoDispatch.launchSerial, so they run one at a time in call order.
    public void insert(Download download) {
        IoDispatch.launchSerial(() -> downloadDao.insert(download));
    }

    public void update(String id) {
        IoDispatch.launchSerial(() -> downloadDao.update(id));
    }

    public void insertAll(List<Download> downloads) {
        IoDispatch.launchSerial(() -> downloadDao.insertAll(downloads));
    }

    public void deleteAll() {
        IoDispatch.launchSerial(downloadDao::deleteAll);
    }

    public void delete(String id) {
        IoDispatch.launchSerial(() -> downloadDao.delete(id));
    }

    public void deleteFailed(String id) {
        IoDispatch.launchSerial(() -> downloadDao.deleteFailed(id));
    }

    public void delete(List<String> ids) {
        IoDispatch.launchSerial(() -> downloadDao.deleteByIds(ids));
    }
}
