package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.ServerDao;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.util.IoDispatch;
import com.eddyizm.tempus.util.SecretBox;

import java.util.ArrayList;
import java.util.List;

public class ServerRepository {
    private static final String TAG = "QueueRepository";

    private final ServerDao serverDao = AppDatabase.getInstance().serverDao();

    // Passwords are sealed in the database and opened here, so callers only see plain passwords.
    public LiveData<List<Server>> getLiveServer() {
        return Transformations.map(serverDao.getAll(), ServerRepository::openPasswords);
    }

    private static List<Server> openPasswords(List<Server> servers) {
        List<Server> opened = new ArrayList<>(servers.size());
        for (Server server : servers) {
            opened.add(ServerPasswordsKt.withPlainPassword(server));
        }
        return opened;
    }

    public void insert(Server server) {
        IoDispatch.launch(() -> serverDao.insert(ServerPasswordsKt.withSealedPassword(server)));
    }

    public void update(Server server) {
        IoDispatch.launch(() -> serverDao.update(ServerPasswordsKt.withSealedPassword(server)));
    }

    /** Seals any password still stored as legacy plaintext. Safe to run on every start. */
    public void sealLegacyPasswords() {
        IoDispatch.launch(() -> {
            for (Server server : serverDao.getAllNow()) {
                if (!SecretBox.INSTANCE.isSealed(server.getPassword())) {
                    serverDao.update(ServerPasswordsKt.withSealedPassword(server));
                }
            }
        });
    }

    public void delete(Server server) {
        IoDispatch.launch(() -> serverDao.delete(server));
    }
}
