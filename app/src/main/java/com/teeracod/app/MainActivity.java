package com.teeracod.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Arrays;

public class MainActivity extends Activity {
    private WebView web;
    private static final String PREFS = "teeera_secure";
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF030202);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "TC");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private String hashPin(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }
    public class Bridge {
        Context ctx;
        Bridge(Context c) { ctx = c; }

        @JavascriptInterface public boolean hasPin() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).contains("pin_hash");
        }
        @JavascriptInterface public boolean setPin(String pin) {
            if (pin == null || !pin.matches("[0-9]{4,8}")) return false;
            try {
                byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
                String h = hashPin(pin, salt);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("pin_hash", h).apply();
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean checkPin(String pin) {
            try {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                if (!p.contains("pin_hash")) return false;
                byte[] salt = Base64.decode(p.getString("pin_salt", ""), Base64.NO_WRAP);
                String got = hashPin(pin, salt), expected = p.getString("pin_hash", "");
                return java.security.MessageDigest.isEqual(got.getBytes("UTF-8"), expected.getBytes("UTF-8"));
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public void copyText(String value) {
            ClipboardManager clipboard = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("TEEERA COD", value));
            Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show();
        }
        @JavascriptInterface public String deviceInfo() {
            return "الشركة: " + Build.MANUFACTURER + "\nالجهاز: " + Build.MODEL +
                "\nأندرويد: " + Build.VERSION.RELEASE + "\nمستوى API: " + Build.VERSION.SDK_INT +
                "\nإصدار البناء: " + Build.DISPLAY;
        }
        @JavascriptInterface public String networkInfo() {
            try {
                ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo n = cm.getActiveNetworkInfo();
                if (n != null && n.isConnected()) return "متصل\nنوع الاتصال: " + n.getTypeName();
                return "غير متصل بالإنترنت";
            } catch (Exception e) { return "تعذر قراءة حالة الشبكة"; }
        }

        // Only fixed, reviewed commands are exposed to Termux. No arbitrary shell input.
        @JavascriptInterface public String runSafeCommand(String id) {
            String command;
            if ("pwd".equals(id)) command = "pwd";
            else if ("date".equals(id)) command = "date";
            else if ("disk".equals(id)) command = "df -h";
            else if ("interfaces".equals(id)) command = "ip addr";
            else if ("python".equals(id)) command = "python --version";
            else if ("packages".equals(id)) command = "pkg list-installed";
            else return "هذا الأمر غير موجود في القائمة الآمنة.";
            try {
                Intent i = new Intent("com.termux.RUN_COMMAND");
                i.setClassName("com.termux", "com.termux.app.RunCommandService");
                i.putExtra(" com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh");
                i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh");
                i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
                i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                return "تم إرسال الأمر إلى Termux: " + command + "\nقد تحتاج تفعيل allow-external-apps في إعدادات Termux.";
            } catch (Exception e) {
                return "تعذر التشغيل. تأكد من تثبيت Termux وتفعيل السماح للتطبيقات الخارجية. " + e.getClass().getSimpleName();
            }
        }
    }
}
