package com.eddyizm.tempus;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;

import com.eddyizm.tempus.github.Github;
import com.eddyizm.tempus.helper.ThemeHelper;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.SubsonicPreferences;
import com.eddyizm.tempus.ui.crash.CrashActivity;
import com.eddyizm.tempus.util.ClientCertManager;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.ServerPreferences;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {
    private static App instance;
    private static Context context;
    private static Subsonic subsonic;
    private static Github github;
    private static SharedPreferences preferences;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        // Capture crash logs
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM) //default: CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM
                .enabled(true) //default: true
                .showErrorDetails(true) //default: true
                .showRestartButton(true) //default: true
                .logErrorOnRestart(true) //default: true
                .trackActivities(false) //default: false
                .minTimeBetweenCrashesMs(3000) //default: 3000
                .errorDrawable(R.drawable.ui_crash) //default: bug image
                .restartActivity(null) //default: null (your app's launch activity)
                .errorActivity(CrashActivity.class) //default: null (default error activity)
                .eventListener(null) //default: null
                .customCrashDataCollector(null) //default: null
                .apply();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String themePref = sharedPreferences.getString(Preferences.THEME, ThemeHelper.DEFAULT_MODE);
        ThemeHelper.applyTheme(themePref);

        instance = new App();
        context = getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        ClientCertManager.setupSslSocketFactory(context);

        // Seal server passwords that were stored in plaintext before SecretBox existed.
        // Runs after the context is set: the database needs App.getContext().
        new com.eddyizm.tempus.repository.ServerRepository().sealLegacyPasswords();
    }

    public static App getInstance() {
        if (instance == null) {
            instance = new App();
        }

        return instance;
    }

    public static Context getContext() {
        if (context == null) {
            context = getInstance();
        }

        return context;
    }

    public static Subsonic getSubsonicClientInstance(boolean override) {
        if (subsonic == null || override) {
            subsonic = getSubsonicClient();
        }
        return subsonic;
    }

    // Pinned to one address instead of the one in use, so an address can be reached without the
    // app being moved onto it first.
    public static Subsonic getSubsonicClientInstance(String serverAddress) {
        return buildSubsonicClient(
                serverAddress,
                ServerPreferences.getUser(),
                ServerPreferences.getPassword(),
                ServerPreferences.getToken(),
                ServerPreferences.getSalt(),
                ServerPreferences.isLowScurity()
        );
    }

    // For a server the app is not signed in to, so it can be reached before anything about it is
    // written to the preferences.
    public static Subsonic getSubsonicClientInstance(Server server) {
        return buildSubsonicClient(
                server.getAddress(),
                server.getUsername(),
                server.getPassword(),
                null,
                null,
                server.isLowSecurity()
        );
    }

    private static Subsonic buildSubsonicClient(String serverAddress, String username,
                                                String password, String token, String salt,
                                                boolean isLowSecurity) {
        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(serverAddress);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return new Subsonic(preferences);
    }

    public static Github getGithubClientInstance() {
        if (github == null) {
            github = new Github();
        }
        return github;
    }

    public SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
        }

        return preferences;
    }

    public static void refreshSubsonicClient() {
        subsonic = getSubsonicClient();
    }

    private static Subsonic getSubsonicClient() {
        SubsonicPreferences preferences = getSubsonicPreferences();

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                ServerPreferences.setPassword(preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getToken() != null)
                ServerPreferences.setToken(preferences.getAuthentication().getToken());
            if (preferences.getAuthentication().getSalt() != null)
                ServerPreferences.setSalt(preferences.getAuthentication().getSalt());
        }

        return new Subsonic(preferences);
    }

    @NonNull
    private static SubsonicPreferences getSubsonicPreferences() {
        String server = ServerPreferences.getInUseServerAddress();
        String username = ServerPreferences.getUser();
        String password = ServerPreferences.getPassword();
        String token = ServerPreferences.getToken();
        String salt = ServerPreferences.getSalt();
        boolean isLowSecurity = ServerPreferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return preferences;
    }
}
