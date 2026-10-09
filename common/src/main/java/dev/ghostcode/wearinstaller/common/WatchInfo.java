package dev.ghostcode.wearinstaller.common;

import org.json.JSONObject;

public final class WatchInfo {
    public final String nodeId, deviceName, model, ip, nonce;
    public final int api, installerVersion;
    public WatchInfo(String nodeId, String deviceName, String model, String ip, String nonce, int api) {
        this(nodeId,deviceName,model,ip,nonce,api,0);
    }
    public WatchInfo(String nodeId, String deviceName, String model, String ip, String nonce, int api, int installerVersion) {
        this.installerVersion=installerVersion;
        this.nodeId = nodeId; this.deviceName = deviceName; this.model = model;
        this.ip = isLocalIpv4(ip) ? ip : ""; this.nonce = nonce; this.api = api;
    }
    public static WatchInfo parse(String nodeId, byte[] bytes) throws Exception {
        JSONObject json = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        return new WatchInfo(nodeId, json.optString("deviceName", "Wear OS Uhr"),
                json.optString("model"), json.optString("ip"), json.optString("nonce"), json.optInt("api", 30), json.optInt("installerVersion",0));
    }
    public static boolean isLocalIpv4(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] p = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("[0-9]{1,3}")) return false;
                p[i] = Integer.parseInt(parts[i]);
                if (p[i] > 255) return false;
            }
        } catch (NumberFormatException e) { return false; }
        return p[0] == 10 || (p[0] == 172 && p[1] >= 16 && p[1] <= 31)
                || (p[0] == 192 && p[1] == 168) || (p[0] == 169 && p[1] == 254);
    }
}
