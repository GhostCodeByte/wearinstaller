package dev.ghostcode.wearinstaller.common;

public final class Protocol {
    public static final String REQUEST = "/wearinstaller/request";
    public static final String INFO = "/wearinstaller/info";
    public static final String DATA = "/wearinstaller/watch";
    public static final String WATCH_CAPABILITY = "wearinstaller_watch";
    public static final String PHONE_CAPABILITY = "wearinstaller_phone";
    public static final String APK_CHANNEL = "/wearinstaller/apk/";
    public static final String INSTALL_STATUS = "/wearinstaller/install/status";
    public static final String INSTALL_QUERY = "/wearinstaller/install/query";
    public static final String INSTALL_CANCEL = "/wearinstaller/install/cancel";
    public static final int INSTALLER_VERSION = 1;
    private Protocol() {}
}
