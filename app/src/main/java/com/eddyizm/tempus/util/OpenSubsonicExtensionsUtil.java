package com.eddyizm.tempus.util;

import com.eddyizm.tempus.subsonic.models.OpenSubsonicExtension;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.List;
import com.eddyizm.tempus.util.ServerPreferences;

public class OpenSubsonicExtensionsUtil {
    private static List<OpenSubsonicExtension> getOpenSubsonicExtensions() {
        List<OpenSubsonicExtension> extensions = null;

        if (ServerPreferences.isOpenSubsonic() && ServerPreferences.getOpenSubsonicExtensions() != null) {
            extensions = new Gson().fromJson(
                    ServerPreferences.getOpenSubsonicExtensions(),
                    new TypeToken<List<OpenSubsonicExtension>>() {
                    }.getType()
            );
        }

        return extensions;
    }

    private static OpenSubsonicExtension getOpenSubsonicExtension(String extensionName) {
        if (getOpenSubsonicExtensions() == null) return null;

        return getOpenSubsonicExtensions().stream().filter(openSubsonicExtension -> openSubsonicExtension.getName().equals(extensionName)).findAny().orElse(null);
    }

    public static boolean isTranscodeOffsetExtensionAvailable() {
        return getOpenSubsonicExtension("transcodeOffset") != null;
    }

    public static boolean isFormPostExtensionAvailable() {
        return getOpenSubsonicExtension("formPost") != null;
    }

    public static boolean isSongLyricsExtensionAvailable() {
        return getOpenSubsonicExtension("songLyrics") != null;
    }
}
