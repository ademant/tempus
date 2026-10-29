package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.ServerDao;
import com.eddyizm.tempus.model.Server;
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
        InsertThreadSafe insert = new InsertThreadSafe(serverDao, server);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void update(Server server) {
        UpdateThreadSafe update = new UpdateThreadSafe(serverDao, server);
        Thread thread = new Thread(update);
        thread.start();
    }

    /** Seals any password still stored as legacy plaintext. Safe to run on every start. */
    public void sealLegacyPasswords() {
        new Thread(() -> {
            for (Server server : serverDao.getAllNow()) {
                if (!SecretBox.INSTANCE.isSealed(server.getPassword())) {
                    serverDao.update(ServerPasswordsKt.withSealedPassword(server));
                }
            }
        }).start();
    }

    public void delete(Server server) {
        DeleteThreadSafe delete = new DeleteThreadSafe(serverDao, server);
        Thread thread = new Thread(delete);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public InsertThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.insert(ServerPasswordsKt.withSealedPassword(server));
        }
    }

    public static class UpdateThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public UpdateThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.update(ServerPasswordsKt.withSealedPassword(server));
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public DeleteThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.delete(server);
        }
    }
}
