package vn.khachbds.app;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Cầu nối giữa giao diện (JavaScript) và Android.
 * Trong JavaScript gọi: Android.loadData(), Android.saveData(json),
 * Android.shareText(tieuDe, noiDung), Android.setReminders(json).
 */
public class Bridge {

    private static final Object LOCK = new Object();
    private static final String DATA = "khachbds_data.json";
    private static final String BACKUP = "khachbds_data.bak";
    private static final String TEMP = "khachbds_data.tmp";

    private final Context ctx;

    Bridge(Context context) {
        ctx = context.getApplicationContext();
    }

    @JavascriptInterface
    public String loadData() {
        synchronized (LOCK) {
            File f = new File(ctx.getFilesDir(), DATA);
            if (!f.exists()) {
                File bak = new File(ctx.getFilesDir(), BACKUP);
                if (!bak.exists()) return "";
                f = bak;
            }
            try {
                return readFile(f);
            } catch (IOException e) {
                return "";
            }
        }
    }

    /** Ghi an toàn: ghi ra file tạm, giữ bản cũ làm .bak rồi mới thay. */
    @JavascriptInterface
    public boolean saveData(String json) {
        if (json == null) return false;
        synchronized (LOCK) {
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, TEMP);
            File data = new File(dir, DATA);
            File bak = new File(dir, BACKUP);
            try {
                FileOutputStream out = new FileOutputStream(tmp);
                try {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                } finally {
                    out.close();
                }
                if (data.exists()) {
                    if (bak.exists()) bak.delete();
                    data.renameTo(bak);
                }
                return tmp.renameTo(data);
            } catch (IOException e) {
                return false;
            }
        }
    }

    @JavascriptInterface
    public void shareText(String title, String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(send, title);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(chooser);
        } catch (RuntimeException ignored) {
            // Dữ liệu quá lớn hoặc không có app nào nhận
        }
    }

    @JavascriptInterface
    public void setReminders(String json) {
        Reminders.save(ctx, json);
        Reminders.scheduleSaved(ctx);
    }

    private static String readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
