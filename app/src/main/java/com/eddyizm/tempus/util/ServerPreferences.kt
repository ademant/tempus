package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.OpenSubsonicExtension
import com.google.gson.Gson

object ServerPreferences {
    private const val SERVER = "server"

    private const val USER = "user"

    private const val PASSWORD = "password"

    private const val TOKEN = "token"

    private const val SALT = "salt"

    private const val LOW_SECURITY = "low_security"

    private const val ALLOW_UNENCRYPTED_SERVER = "allow_unencrypted_server"

    private const val CLIENT_CERT = "client_cert"

    private const val CUSTOM_HEADERS = "custom_headers"

    private const val BATTERY_OPTIMIZATION = "battery_optimization"

    private const val SERVER_ID = "server_id"

    private const val OPEN_SUBSONIC = "open_subsonic"

    private const val OPEN_SUBSONIC_EXTENSIONS = "open_subsonic_extensions"

    private const val LOCAL_ADDRESS = "local_address"

    private const val IN_USE_SERVER_ADDRESS = "in_use_server_address"

    private const val NEXT_SERVER_SWITCH = "next_server_switch"

    private const val SERVER_UNREACHABLE = "server_unreachable"

    private const val AUDIO_TRANSCODE_PRIORITY = "audio_transcode_priority"

    private const val SHARE = "share"

    private const val SCROBBLING = "scrobbling"

    private const val NETWORK_PING_TIMEOUT = "network_ping_timeout_base"

	@JvmStatic
    fun getServer(): String? {
        return App.getInstance().preferences.getString(SERVER, null)
    }

    @JvmStatic
    fun setServer(server: String?) {
        App.getInstance().preferences.edit().putString(SERVER, server).apply()
    }

    @JvmStatic
    fun getNetworkPingTimeout(): Int {
        val timeoutString = App.getInstance().preferences.getString(NETWORK_PING_TIMEOUT, "2") ?: "2"
        return (timeoutString.toIntOrNull() ?: 2).coerceAtLeast(1)
    }

    @JvmStatic
    fun setNetworkPingTimeout(pingTimeout: String?) {
        App.getInstance().preferences.edit().putString(NETWORK_PING_TIMEOUT, pingTimeout).apply()
    }

    @JvmStatic
    fun getUser(): String? {
        return App.getInstance().preferences.getString(USER, null)
    }

    @JvmStatic
    fun setUser(user: String?) {
        App.getInstance().preferences.edit().putString(USER, user).apply()
    }

    // Sealed with SecretBox. Legacy plaintext is re-sealed the first time it is read.
    @JvmStatic
    fun getPassword(): String? {
        val stored = App.getInstance().preferences.getString(PASSWORD, null) ?: return null
        if (!SecretBox.isSealed(stored)) {
            setPassword(stored)
            return stored
        }
        return SecretBox.open(stored)
    }

    @JvmStatic
    fun setPassword(password: String?) {
        App.getInstance().preferences.edit().putString(PASSWORD, SecretBox.seal(password)).apply()
    }

    @JvmStatic
    fun getToken(): String? {
        return App.getInstance().preferences.getString(TOKEN, null)
    }

    @JvmStatic
    fun setToken(token: String?) {
        App.getInstance().preferences.edit().putString(TOKEN, token).apply()
    }

    @JvmStatic
    fun getSalt(): String? {
        return App.getInstance().preferences.getString(SALT, null)
    }

    @JvmStatic
    fun setSalt(salt: String?) {
        App.getInstance().preferences.edit().putString(SALT, salt).apply()
    }

    @JvmStatic
    fun isLowScurity(): Boolean {
        return App.getInstance().preferences.getBoolean(LOW_SECURITY, false)
    }

    // Servers saved before this flag existed keep working over HTTP, so the default is true.
    @JvmStatic
    fun isAllowUnencryptedServer(): Boolean {
        return App.getInstance().preferences.getBoolean(ALLOW_UNENCRYPTED_SERVER, true)
    }

    @JvmStatic
    fun setAllowUnencryptedServer(allow: Boolean) {
        App.getInstance().preferences.edit().putBoolean(ALLOW_UNENCRYPTED_SERVER, allow).apply()
    }

    @JvmStatic
    fun setLowSecurity(isLowSecurity: Boolean) {
        App.getInstance().preferences.edit().putBoolean(LOW_SECURITY, isLowSecurity).apply()
    }

    @JvmStatic
    fun getClientCert(): String? {
        return App.getInstance().preferences.getString(CLIENT_CERT, null)
    }

    @JvmStatic
    fun setClientCert(clientCert: String?) {
        App.getInstance().preferences.edit().putString(CLIENT_CERT, clientCert).apply()
    }

    @JvmStatic
    fun getCustomHeaders(): String? {
        return App.getInstance().preferences.getString(CUSTOM_HEADERS, null)
    }

    @JvmStatic
    fun setCustomHeaders(customHeaders: String?) {
        App.getInstance().preferences.edit().putString(CUSTOM_HEADERS, customHeaders).apply()
    }

    @JvmStatic
    fun getServerId(): String? {
        return App.getInstance().preferences.getString(SERVER_ID, null)
    }

    @JvmStatic
    fun setServerId(serverId: String?) {
        App.getInstance().preferences.edit().putString(SERVER_ID, serverId).apply()
    }

    @JvmStatic
    fun isOpenSubsonic(): Boolean {
        return App.getInstance().preferences.getBoolean(OPEN_SUBSONIC, false)
    }

    @JvmStatic
    fun setOpenSubsonic(isOpenSubsonic: Boolean) {
        App.getInstance().preferences.edit().putBoolean(OPEN_SUBSONIC, isOpenSubsonic).apply()
    }

    @JvmStatic
    fun getOpenSubsonicExtensions(): String? {
        return App.getInstance().preferences.getString(OPEN_SUBSONIC_EXTENSIONS, null)
    }

    @JvmStatic
    fun setOpenSubsonicExtensions(extension: List<OpenSubsonicExtension>) {
        App.getInstance().preferences.edit().putString(OPEN_SUBSONIC_EXTENSIONS, Gson().toJson(extension)).apply()
    }

    @JvmStatic
    fun getLocalAddress(): String? {
        return App.getInstance().preferences.getString(LOCAL_ADDRESS, null)
    }

    @JvmStatic
    fun setLocalAddress(address: String?) {
        App.getInstance().preferences.edit().putString(LOCAL_ADDRESS, address).apply()
    }

    @JvmStatic
    fun getInUseServerAddress(): String? {
        return App.getInstance().preferences.getString(IN_USE_SERVER_ADDRESS, null)
            ?.takeIf { it.isNotBlank() }
            ?: getServer()
    }

    @JvmStatic
    fun setInUseServerAddress(address: String?) {
        App.getInstance().preferences.edit().putString(IN_USE_SERVER_ADDRESS, address).apply()
    }

    @JvmStatic
    fun isInUseServerAddressLocal(): Boolean {
        return getInUseServerAddress() == getLocalAddress()
    }

    @JvmStatic
    fun switchInUseServerAddress() {
        val inUseAddress = if (getInUseServerAddress() == getServer()) getLocalAddress() else getServer()
        App.getInstance().preferences.edit().putString(IN_USE_SERVER_ADDRESS, inUseAddress).apply()
    }

    @JvmStatic
    fun isServerSwitchable(): Boolean {
        return App.getInstance().preferences.getLong(
                NEXT_SERVER_SWITCH, 0
        ) + 15000 < System.currentTimeMillis() && !getServer().isNullOrEmpty() && !getLocalAddress().isNullOrEmpty()
    }

    @JvmStatic
    fun setServerSwitchableTimer() {
        App.getInstance().preferences.edit().putLong(NEXT_SERVER_SWITCH, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun askForOptimization(): Boolean {
        return App.getInstance().preferences.getBoolean(BATTERY_OPTIMIZATION, true)
    }

    @JvmStatic
    fun dontAskForOptimization() {
        App.getInstance().preferences.edit().putBoolean(BATTERY_OPTIMIZATION, false).apply()
    }

    @JvmStatic
    fun showServerUnreachableDialog(): Boolean {
        return App.getInstance().preferences.getLong(
                SERVER_UNREACHABLE, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setServerUnreachableDatetime() {
        App.getInstance().preferences.edit().putLong(SERVER_UNREACHABLE, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isServerPrioritized(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_PRIORITY, false)
    }

    @JvmStatic
    fun isSharingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SHARE, false)
    }

    @JvmStatic
    fun isScrobblingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SCROBBLING, true)
    }

    // Off until the user turns them on, so an update starts nothing on the local network by itself.
    @JvmStatic
    fun isUpnpEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(Preferences.UPNP_ENABLED, false)
    }

    @JvmStatic
    fun isLanRemoteEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(Preferences.LAN_REMOTE_ENABLED, false)
    }
}
