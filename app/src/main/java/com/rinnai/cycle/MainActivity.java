package com.rinnai.cycle;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_NOTIFICATIONS = 100;
    private static final String NFC_TRIGGER_URI = "rinnai://cycle";
    private static final int NFC_CYCLE_MINUTES = 1;

    private EditText phone;
    private EditText password;
    private EditText mac;
    private EditText classId;
    private EditText authCode;
    private EditText minutes;
    private TextView status;
    private View settingsContent;
    private ImageView settingsChevron;
    private BroadcastReceiver receiver;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;
    private boolean nfcWriteMode;
    private View nfcWriteHint;
    private Button writeNfcButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phone = findViewById(R.id.phone);
        password = findViewById(R.id.password);
        mac = findViewById(R.id.mac);
        classId = findViewById(R.id.classId);
        authCode = findViewById(R.id.authCode);
        minutes = findViewById(R.id.minutes);
        status = findViewById(R.id.status);

        Config cfg = Config.load(this);
        phone.setText(cfg.phone);
        password.setText(cfg.password);
        mac.setText(cfg.mac);
        classId.setText(cfg.classId);
        authCode.setText(cfg.authCode);
        minutes.setText(String.valueOf(cfg.minutes));

        settingsContent = findViewById(R.id.settingsContent);
        settingsChevron = findViewById(R.id.settingsChevron);
        nfcWriteHint = findViewById(R.id.nfcWriteHint);
        writeNfcButton = findViewById(R.id.writeNfcButton);
        View settingsHeader = findViewById(R.id.settingsHeader);
        boolean needsSetup = cfg.phone.trim().isEmpty() || cfg.password.isEmpty();
        setSettingsExpanded(needsSetup, false);
        settingsHeader.setOnClickListener(v ->
                setSettingsExpanded(settingsContent.getVisibility() != View.VISIBLE, true));

        findViewById(R.id.startButton).setOnClickListener(v -> startCycle());
        findViewById(R.id.stopButton).setOnClickListener(v -> stopCycle());
        writeNfcButton.setOnClickListener(v -> toggleWriteMode());

        initNfc();

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(CycleService.EXTRA_STATUS);
                if (text != null) {
                    status.setText(text);
                }
            }
        };
        ContextCompat.registerReceiver(
                this, receiver, new IntentFilter(CycleService.ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void initNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPendingIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void startCycle() {
        Config cfg = new Config();
        cfg.phone = phone.getText().toString().trim();
        cfg.password = password.getText().toString();
        cfg.mac = mac.getText().toString().trim();
        cfg.classId = classId.getText().toString().trim();
        cfg.authCode = authCode.getText().toString().trim();
        try {
            cfg.minutes = Integer.parseInt(minutes.getText().toString().trim());
        } catch (Exception e) {
            cfg.minutes = 3;
        }
        if (cfg.minutes < 1) {
            cfg.minutes = 1;
        }

        if (cfg.phone.isEmpty() || cfg.password.isEmpty() || cfg.mac.isEmpty()
                || cfg.classId.isEmpty() || cfg.authCode.isEmpty()) {
            Toast.makeText(this, "请先填写完整配置", Toast.LENGTH_SHORT).show();
            return;
        }
        cfg.save(this);

        Intent intent = new Intent(this, CycleService.class);
        intent.setAction(CycleService.ACTION_START);
        intent.putExtra(CycleService.EXTRA_MINUTES, cfg.minutes);
        ContextCompat.startForegroundService(this, intent);
        status.setText("正在启动…");
    }

    private void startCycleFromNfc() {
        Config cfg = Config.load(this);
        if (cfg.phone.trim().isEmpty() || cfg.password.isEmpty() || cfg.mac.isEmpty()
                || cfg.classId.isEmpty() || cfg.authCode.isEmpty()) {
            Toast.makeText(this, "请先在 App 内填写设备配置", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, CycleService.class);
        intent.setAction(CycleService.ACTION_START);
        intent.putExtra(CycleService.EXTRA_MINUTES, NFC_CYCLE_MINUTES);
        ContextCompat.startForegroundService(this, intent);
        status.setText("NFC 触发 · 启动 1 分钟循环…");
    }

    private void toggleWriteMode() {
        if (nfcWriteMode) {
            exitWriteMode();
        } else {
            enterWriteMode();
        }
    }

    private void enterWriteMode() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "本机不支持 NFC", Toast.LENGTH_SHORT).show();
            return;
        }
        nfcWriteMode = true;
        nfcWriteHint.setVisibility(View.VISIBLE);
        writeNfcButton.setText("取消写入");
        writeNfcButton.setSelected(true);
    }

    private void exitWriteMode() {
        nfcWriteMode = false;
        nfcWriteHint.setVisibility(View.GONE);
        writeNfcButton.setText("写入 NFC 触发标签");
        writeNfcButton.setSelected(false);
    }

    private void writeNfcTag(Intent intent) {
        Tag tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag.class);
        if (tag == null) {
            Toast.makeText(this, "无法读取标签", Toast.LENGTH_SHORT).show();
            exitWriteMode();
            return;
        }
        NdefMessage message = new NdefMessage(new NdefRecord[]{
                NdefRecord.createUri(NFC_TRIGGER_URI)
        });
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    ndef.close();
                    Toast.makeText(this, "标签只读，无法写入", Toast.LENGTH_SHORT).show();
                } else {
                    ndef.writeNdefMessage(message);
                    ndef.close();
                    Toast.makeText(this, "写入成功！贴标签即可启动循环", Toast.LENGTH_LONG).show();
                }
            } else {
                NdefFormatable fmt = NdefFormatable.get(tag);
                if (fmt == null) {
                    Toast.makeText(this, "标签不支持 NDEF", Toast.LENGTH_SHORT).show();
                } else {
                    fmt.connect();
                    fmt.format(message);
                    fmt.close();
                    Toast.makeText(this, "写入成功！贴标签即可启动循环", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "写入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        exitWriteMode();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (nfcWriteMode) {
            writeNfcTag(intent);
        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            startCycleFromNfc();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcWriteMode && nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(
                    this, nfcPendingIntent, null, null);
        }
        Intent current = getIntent();
        if (current != null && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(current.getAction())) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            startCycleFromNfc();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcWriteMode && nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    private void setSettingsExpanded(boolean expanded, boolean animate) {
        settingsContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        settingsChevron.animate()
                .rotation(expanded ? 180f : 0f)
                .setDuration(animate ? 200 : 0)
                .start();
        if (expanded && animate) {
            settingsContent.setAlpha(0f);
            settingsContent.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void stopCycle() {
        Intent intent = new Intent(this, CycleService.class);
        intent.setAction(CycleService.ACTION_STOP);
        ContextCompat.startForegroundService(this, intent);
        status.setText("正在关闭…");
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        if (nfcWriteMode && nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
        super.onDestroy();
    }
}
