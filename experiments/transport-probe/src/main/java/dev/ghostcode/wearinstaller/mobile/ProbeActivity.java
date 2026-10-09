package dev.ghostcode.wearinstaller.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Foreground laboratory only: authenticated Wear peers, loopback-only relay, explicit actions. */
public final class ProbeActivity extends Activity {
 private static volatile ProbeActivity visible;
 private final ExecutorService work = Executors.newCachedThreadPool();
 private TextView output;
 private EditText host, port, pin;
 private boolean watch;
 private AdbDiscovery discovery;
 private final ChannelClient.ChannelCallback channels = new ChannelClient.ChannelCallback() {
  @Override public void onChannelOpened(ChannelClient.Channel channel) {
   if (watch) work.execute(() -> receive(channel));
  }
 };
 private final MessageClient.OnMessageReceivedListener messages = event -> log("REMOTE " + new String(event.getData(), StandardCharsets.UTF_8));
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); visible = this;
  watch = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
  ScrollView scroll = new ScrollView(this); LinearLayout layout = new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24,watch ? 36 : 100,24,24); scroll.addView(layout); setContentView(scroll);
  output = new TextView(this); output.setTextSize(watch ? 12 : 14);
  if (!watch) {
   host = field(layout,"Watch IP","10.0.2.18"); port = field(layout,"ADB port",""); pin = field(layout,"Pairing code","");
   button(layout,"Peers",() -> work.execute(() -> { try { log("PEERS=" + Tasks.await(Wearable.getNodeClient(this).getConnectedNodes(),15,TimeUnit.SECONDS)); } catch(Exception e) { failure(e); } }));
   button(layout,"Discover ADB",() -> {
    if(discovery!=null) discovery.close();
    discovery=new AdbDiscovery(this,new AdbDiscovery.Listener() {
     public void changed(AdbDiscovery.Endpoint connect,AdbDiscovery.Endpoint pairing) { log("DISCOVERY connect="+connect+" pairing="+pairing); }
     public void error(String error) { log("DISCOVERY_ERROR="+error); }
    });
    discovery.start(host.getText().toString());
   });
   button(layout,"Direct pair",() -> adbAction(false,true));
   button(layout,"Direct install",() -> adbAction(false,false));
   button(layout,"Relay pair",() -> adbAction(true,true));
   button(layout,"Relay install",() -> adbAction(true,false));
   button(layout,"Relay shell",() -> adbAction(true,false,false));
   button(layout,"Channel + PackageInstaller",() -> work.execute(this::sendPackage));
  } else {
   button(layout,"Unknown app settings",() -> {
    log("CAN_REQUEST_INSTALLS=" + getPackageManager().canRequestPackageInstalls());
    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
    log("UNKNOWN_SOURCES_HANDLER=" + getPackageManager().resolveActivity(intent,0));
    try { startActivity(intent); } catch(Exception e) { failure(e); }
   });
   button(layout,"Install cached APK",() -> work.execute(() -> { try { installPackage(new File(getFilesDir(),"received.apk")); } catch(Exception e) { failure(e); } }));
  }
  layout.addView(output);
  Wearable.getChannelClient(this).registerChannelCallback(channels);
  Wearable.getMessageClient(this).addListener(messages);
  log("READY watch=" + watch + " sdk=" + Build.VERSION.SDK_INT + " uid=" + android.os.Process.myUid() + " canInstall=" + getPackageManager().canRequestPackageInstalls());
 }
 private EditText field(LinearLayout layout,String hint,String value) {
  EditText field = new EditText(this); field.setHint(hint); field.setContentDescription(hint); field.setText(value); field.setSingleLine(true); layout.addView(field); return field;
 }
 private void button(LinearLayout layout,String text,Runnable action) {
  Button button = new Button(this); button.setText(text); button.setOnClickListener(v -> action.run()); layout.addView(button);
 }
 static void logExternal(String message) {
  android.util.Log.i("TransportProbe",message); ProbeActivity activity = visible;
  if (activity != null) activity.runOnUiThread(() -> activity.output.append(message + "\n"));
 }
 private void log(String message) { logExternal(message); }
 private void failure(Exception e) { android.util.Log.e("TransportProbe","FAILED",e); log("FAILED=" + e); }
 private String peer() throws Exception {
  List<Node> nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes(),15,TimeUnit.SECONDS);
  if(nodes.size()!=1) throw new IOException("Expected one peer, got " + nodes);
  log("PEER=" + nodes.get(0)); return nodes.get(0).getId();
 }
 private File payload() throws Exception {
  File file = new File(getFilesDir(),"payload.apk");
  try(InputStream in=getAssets().open("payload.apk"); OutputStream out=new FileOutputStream(file)) { Streams.copy(in,out); }
  return file;
 }
 private static String hash(File file) throws Exception {
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(file)) { byte[] buf=new byte[32768]; int n; while((n=in.read(buf))!=-1) digest.update(buf,0,n); }
  StringBuilder out=new StringBuilder(); for(byte b:digest.digest()) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
 }
 private void sendPackage() {
  ChannelClient.Channel channel=null;
  try {
   File file=payload(); long start=SystemClock.elapsedRealtime();
   channel=Tasks.await(Wearable.getChannelClient(this).openChannel(peer(),"/probe/package/"+UUID.randomUUID()),15,TimeUnit.SECONDS);
   try(DataOutputStream out=new DataOutputStream(Tasks.await(Wearable.getChannelClient(this).getOutputStream(channel),15,TimeUnit.SECONDS)); InputStream in=new FileInputStream(file)) {
    out.writeLong(file.length()); out.writeUTF(hash(file)); Streams.copy(in,out); out.flush();
   }
   log("CHANNEL_SENT bytes="+file.length()+" sha256="+hash(file)+" ms="+(SystemClock.elapsedRealtime()-start));
  } catch(Exception e) { failure(e); }
  finally { if(channel!=null) Wearable.getChannelClient(this).close(channel); }
 }
 private void receive(ChannelClient.Channel channel) {
  try {
   if(channel.getPath().startsWith("/probe/relay/")) {
    int remotePort=Integer.parseInt(channel.getPath().substring("/probe/relay/".length()).split("/")[0]);
    if(remotePort<=0||remotePort>65535) throw new IOException("Invalid port");
    Socket socket=new Socket(); socket.connect(new InetSocketAddress("127.0.0.1",remotePort),10000); socket.setTcpNoDelay(true);
    bridge(channel,socket); log("RELAY_LOCAL_CONNECTED port="+remotePort); return;
   }
   if(!channel.getPath().startsWith("/probe/package/")) throw new IOException("Unknown channel");
   long start=SystemClock.elapsedRealtime(); File file=new File(getFilesDir(),"received.apk");
   String expected;
   try(DataInputStream in=new DataInputStream(Tasks.await(Wearable.getChannelClient(this).getInputStream(channel),15,TimeUnit.SECONDS)); OutputStream out=new FileOutputStream(file)) {
    long remaining=in.readLong(); if(remaining<1||remaining>64*1024*1024) throw new IOException("Invalid size"); expected=in.readUTF();
    byte[] buf=new byte[32768]; while(remaining>0) { int n=in.read(buf,0,(int)Math.min(buf.length,remaining)); if(n<0) throw new EOFException(); out.write(buf,0,n); remaining-=n; }
   }
   String actual=hash(file); if(!expected.equals(actual)) throw new IOException("Hash mismatch");
   String message="CHANNEL_RECEIVED bytes="+file.length()+" sha256="+actual+" ms="+(SystemClock.elapsedRealtime()-start);
   log(message); Wearable.getMessageClient(this).sendMessage(channel.getNodeId(),"/probe/result",message.getBytes(StandardCharsets.UTF_8));
   installPackage(file);
  } catch(Exception e) { failure(e); Wearable.getChannelClient(this).close(channel); }
 }
 private void installPackage(File file) throws Exception {
  PackageInstaller installer=getPackageManager().getPackageInstaller();
  PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
  params.setSize(file.length());
  if(Build.VERSION.SDK_INT>=31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
  int id=installer.createSession(params); log("PACKAGE_SESSION="+id+" canInstall="+getPackageManager().canRequestPackageInstalls());
  try(PackageInstaller.Session session=installer.openSession(id)) {
   try(InputStream in=new FileInputStream(file); OutputStream out=session.openWrite("base.apk",0,file.length())) { Streams.copy(in,out); session.fsync(out); }
   Intent result=new Intent(this,ProbeInstallReceiver.class).setAction("probe.install."+id);
   PendingIntent pending=PendingIntent.getBroadcast(this,id,result,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
   session.commit(pending.getIntentSender()); log("PACKAGE_COMMITTED="+id);
  }
 }
 private void bridge(ChannelClient.Channel channel,Socket socket) throws Exception {
  ChannelClient client=Wearable.getChannelClient(this);
  InputStream channelIn=Tasks.await(client.getInputStream(channel),15,TimeUnit.SECONDS);
  OutputStream channelOut=Tasks.await(client.getOutputStream(channel),15,TimeUnit.SECONDS);
  work.execute(() -> { try { Streams.copy(channelIn,socket.getOutputStream()); socket.shutdownOutput(); } catch(Exception e) { log("PIPE_IN="+e); } });
  work.execute(() -> { try { Streams.copy(socket.getInputStream(),channelOut); channelOut.close(); } catch(Exception e) { log("PIPE_OUT="+e); } finally { try { socket.close(); } catch(Exception ignored) {} client.close(channel); } });
 }
 private void adbAction(boolean relay,boolean pairing) {
  adbAction(relay,pairing,true);
 }
 private void adbAction(boolean relay,boolean pairing,boolean install) {
  final String targetHost=host.getText().toString(), code=pin.getText().toString();
  final int targetPort;
  try { targetPort=Integer.parseInt(port.getText().toString()); } catch(Exception e) { failure(e); return; }
  work.execute(() -> {
   ChannelClient.Channel channel=null; ServerSocket server=null;
   long start=SystemClock.elapsedRealtime();
   try(AdbIdentity adb=new AdbIdentity(this)) {
    int localPort=targetPort; String address=targetHost;
    if(relay) {
     server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1")); server.setSoTimeout(20000);
     localPort=server.getLocalPort(); address="127.0.0.1";
     channel=Tasks.await(Wearable.getChannelClient(this).openChannel(peer(),"/probe/relay/"+targetPort+"/"+UUID.randomUUID()),15,TimeUnit.SECONDS);
     final ServerSocket listener=server; final ChannelClient.Channel pipe=channel;
     work.execute(() -> { try { Socket socket=listener.accept(); socket.setTcpNoDelay(true); bridge(pipe,socket); } catch(Exception e) { failure(e); } });
    }
    adb.setApi(34);
    if(pairing) { log("PAIR_RESULT="+adb.pair(address,localPort,code)+" relay="+relay+" ms="+(SystemClock.elapsedRealtime()-start)); }
    else {
     if(!adb.connect(address,localPort)) throw new IOException("ADB connect returned false");
     ApkInstaller installer=new ApkInstaller(adb);
     log("ADB_SHELL relay="+relay+" result="+installer.shell("id; getprop ro.build.version.sdk").trim());
     if(!install) { log("ADB_SHELL_OK relay="+relay+" ms="+(SystemClock.elapsedRealtime()-start)); return; }
     File apk=payload(); installer.install(apk,(percent,stage) -> { if(percent==100) log("ADB_INSTALL_DONE relay="+relay); });
     log("ADB_INSTALL_OK relay="+relay+" bytes="+apk.length()+" sha256="+hash(apk)+" ms="+(SystemClock.elapsedRealtime()-start));
    }
   } catch(Exception e) { failure(e); }
   finally { if(server!=null) try { server.close(); } catch(Exception ignored) {} if(channel!=null) Wearable.getChannelClient(this).close(channel); }
  });
 }
 @Override public void onDestroy() {
  Wearable.getChannelClient(this).unregisterChannelCallback(channels); Wearable.getMessageClient(this).removeListener(messages);
  if(discovery!=null) discovery.close();
  if(visible==this) visible=null; work.shutdownNow(); super.onDestroy();
 }
}
