package dev.ghostcode.wearinstaller.mobile;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.ghostcode.wearinstaller.common.Ui;
import com.google.android.gms.wearable.Node;
import java.util.List;

public class MainActivity extends Activity {
    private PhoneController controller;
    private TextView watchName, watchStatus, wireless, adb, message, file, progressText, pairingStatus;
    private ProgressBar progress;
    private Button choose, install, pair, retry, switchWatch, cancel;
    private RadioButton viaChannel, viaAdb;
    private TextView modeHint, footer;
    private LinearLayout pairing;
    private EditText code;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller=(PhoneController)getLastNonConfigurationInstance();
        if(controller==null) controller=new PhoneController(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this,24);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(Ui.dp(this,24),bars.top+Ui.dp(this,20),Ui.dp(this,24),bars.bottom+Ui.dp(this,24)); return insets;
        });
        root.addView(Ui.text(this,"WEAR INSTALLER",12,Ui.GREEN));
        root.addView(Ui.title(this,"Deine Apps.\nAuf deiner Uhr.",32));
        root.addView(Ui.text(this,"APK auswählen und direkt installieren.",15,Ui.MUTED)); Ui.gap(root,24);
        LinearLayout card=Ui.column(this,20); card.setBackground(Ui.shape(Ui.CARD,Ui.dp(this,24)));
        watchName=Ui.title(this,"Uhr suchen …",24); card.addView(watchName);
        watchStatus=Ui.text(this,"Uhr nicht verbunden",15,Ui.MUTED); card.addView(watchStatus); Ui.gap(card,12);
        wireless=Ui.text(this,"Wireless Debugging suchen …",15,Ui.MUTED); card.addView(wireless);
        adb=Ui.text(this,"ADB nicht verbunden",15,Ui.MUTED); card.addView(adb);
        root.addView(card);
        Ui.gap(root,16); root.addView(Ui.title(this,"Installationsweg",20));
        RadioGroup modes=new RadioGroup(this);
        viaChannel=new RadioButton(this); viaChannel.setId(View.generateViewId()); viaChannel.setText("Über Uhr-Verbindung"); viaChannel.setTextColor(Ui.WHITE); modes.addView(viaChannel);
        viaAdb=new RadioButton(this); viaAdb.setId(View.generateViewId()); viaAdb.setText("Über ADB · wie bisher"); viaAdb.setTextColor(Ui.WHITE); modes.addView(viaAdb);
        root.addView(modes); modeHint=Ui.text(this,"",13,Ui.MUTED); root.addView(modeHint);
        modes.check(controller.channelMode?viaChannel.getId():viaAdb.getId());
        modes.setOnCheckedChangeListener((group,id) -> controller.setChannelMode(id==viaChannel.getId()));
        switchWatch=Ui.button(this,"Andere Uhr wählen",false); root.addView(switchWatch); switchWatch.setOnClickListener(v -> selectWatch());
        message=Ui.text(this,"",15,Ui.MUTED); message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(message); Ui.gap(root,12);
        choose=Ui.button(this,"APK auswählen",false); root.addView(choose);
        choose.setOnClickListener(v -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(intent,10);
        });
        root.addView(Ui.text(this,"AUSGEWÄHLTE DATEI",11,Ui.MUTED)); file=Ui.text(this,"Keine APK ausgewählt",16,Ui.WHITE); root.addView(file);
        install=Ui.button(this,"Auf Uhr installieren",true); root.addView(install); install.setOnClickListener(v -> controller.install());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        progressText=Ui.text(this,"",14,Ui.GREEN); root.addView(progressText);
        cancel=Ui.button(this,"Installation abbrechen",false); root.addView(cancel); cancel.setOnClickListener(v -> controller.cancelInstall());
        pairing=Ui.column(this,20); pairing.setBackground(Ui.shape(Ui.CARD,Ui.dp(this,24))); Ui.gap(root,16); root.addView(pairing);
        pairing.addView(Ui.title(this,"ADB einrichten",22));
        pairing.addView(Ui.text(this,"Einmal mit deiner Uhr koppeln.",14,Ui.MUTED));
        pairing.addView(Ui.text(this,"Öffne auf der Uhr:\nEinstellungen → Entwickleroptionen\n→ Wireless Debugging\n→ Gerät mit Kopplungscode koppeln",15,Ui.WHITE));
        pairingStatus=Ui.text(this,"Pairing-Port wird automatisch gesucht …",13,Ui.MUTED); pairing.addView(pairingStatus);
        pairing.addView(Ui.text(this,"Pairing-Code",13,Ui.MUTED));
        code=new EditText(this); code.setId(R.id.pairing_code); code.setHint("6-stelliger Code"); code.setTextColor(Ui.WHITE); code.setHintTextColor(Ui.MUTED);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        code.setSingleLine(true); code.setContentDescription("Sechsstelliger Pairing-Code"); pairing.addView(code);
        code.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { render(); } public void afterTextChanged(Editable e) {} });
        pair=Ui.button(this,"Koppeln",true); pairing.addView(pair); pair.setOnClickListener(v -> { String pin=code.getText().toString(); code.setText(""); controller.pair(pin); });
        retry=Ui.button(this,"Verbindung erneut suchen",false); root.addView(retry); retry.setOnClickListener(v -> controller.retry());
        Ui.gap(root,16); footer=Ui.text(this,"",12,Ui.MUTED); root.addView(footer);
        scroll.addView(root); setContentView(scroll); controller.observer=this::render; render();
    }
    private void render() {
        if(pair==null) return;
        watchName.setText(controller.watch==null?"Uhr suchen …":controller.watch.deviceName);
        watchStatus.setText(controller.watch==null?"Uhr nicht verbunden":"Uhr verbunden ✓"); watchStatus.setTextColor(controller.watch==null?Ui.MUTED:Ui.GREEN);
        viaChannel.setEnabled(!controller.busy); viaAdb.setEnabled(!controller.busy);
        modeHint.setText(controller.channelMode?"Kein gemeinsames WLAN und kein ADB nötig. Neue Apps auf der Uhr bestätigen. Updates ohne Dialog, soweit Android es erlaubt.":"Nach einmaliger ADB-Kopplung ohne Installationsdialog auf der Uhr. Gemeinsames WLAN oder Handy-Hotspot und Wireless Debugging nötig.");
        wireless.setVisibility(controller.channelMode?View.GONE:View.VISIBLE); adb.setVisibility(controller.channelMode?View.GONE:View.VISIBLE);
        footer.setText(controller.channelMode?"Handy und Uhr über die offizielle Begleit-App verbinden. Beide Wear-Installer-Apps auf Version 1.2.0 aktualisieren.":"Handy und Uhr im selben WLAN oder Handy-Hotspot. Dein ADB-Schlüssel bleibt auf diesem Handy.");
        cancel.setVisibility(controller.channelPending()?View.VISIBLE:View.GONE);
        boolean debugging=controller.connect!=null;
        wireless.setText(debugging?"Wireless Debugging ✓":"Wireless Debugging suchen …"); wireless.setTextColor(debugging?Ui.GREEN:Ui.MUTED);
        adb.setText(controller.adbConnected?"ADB verbunden ✓":"ADB nicht verbunden"); adb.setTextColor(controller.adbConnected?Ui.GREEN:Ui.MUTED);
        message.setText(controller.message); file.setText(controller.fileName);
        choose.setEnabled(!controller.busy); install.setEnabled(controller.canInstall());
        choose.setAlpha(choose.isEnabled()?1:.45f); install.setAlpha(install.isEnabled()?1:.45f);
        pairing.setVisibility(!controller.channelMode && !controller.adbConnected && (controller.watch==null || controller.pairingNeeded || controller.pairing!=null)?View.VISIBLE:View.GONE);
        pairingStatus.setText(controller.pairing!=null?"Pairing-Port gefunden ✓":"Pairing-Port wird automatisch gesucht …");
        pair.setEnabled(!controller.busy && controller.pairing!=null && code.getText().length()==6); pair.setAlpha(pair.isEnabled()?1:.45f);
        code.setEnabled(!controller.busy); retry.setEnabled(!controller.busy); retry.setText(controller.channelMode?"Uhr erneut suchen":"Verbindung erneut suchen");
        switchWatch.setVisibility(controller.watches().size()>1?View.VISIBLE:View.GONE); switchWatch.setEnabled(!controller.busy);
        progress.setVisibility(controller.busy || controller.progress>0?View.VISIBLE:View.GONE); progress.setProgress(controller.progress);
        progress.setIndeterminate(controller.busy && controller.progress==0);
        progressText.setText(controller.stage.isEmpty()?(controller.progress==100?"100 % · Installation abgeschlossen":""):controller.stage+(controller.progress>0?" "+controller.progress+" %":""));
        if(controller.busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void selectWatch() {
        List<Node> nodes=controller.watches(); String[] labels=nodes.stream().map(Node::getDisplayName).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Uhr auswählen").setItems(labels,(d,i) -> controller.selectWatch(nodes.get(i).getId())).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=10 || result!=RESULT_OK || data==null || data.getData()==null) return;
        String name="selected.apk";
        try(Cursor cursor=getContentResolver().query(data.getData(),new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) name=cursor.getString(0);
        } catch(Exception ignored) {}
        controller.selectApk(data.getData(),name);
    }
    @Override public Object onRetainNonConfigurationInstance() { controller.observer=null; return controller; }
    @Override protected void onDestroy() { if(!isChangingConfigurations()) controller.close(); else controller.observer=null; super.onDestroy(); }
}
