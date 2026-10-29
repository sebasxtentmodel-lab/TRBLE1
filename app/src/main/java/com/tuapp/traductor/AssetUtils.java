package com.tuapp.traductor;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class AssetUtils {
    private AssetUtils() {}
    static File extractDirectory(Context ctx, String name) throws IOException {
        File out = new File(ctx.getFilesDir(), name);
        if (new File(out, ".complete-v2").isFile()) return out;
        String[] children = ctx.getAssets().list(name);
        if (children == null || children.length == 0) {
            throw new IOException("Modelo ausente del APK: " + name);
        }
        copyRecursive(ctx.getAssets(), name, out);
        if (!new File(out, ".complete-v2").createNewFile())
            throw new IOException("No se pudo verificar la copia de assets de " + name);
        return out;
    }
    private static void copyRecursive(AssetManager a, String from, File out) throws IOException {
        String[] children = a.list(from);
        if (children == null) throw new IOException("Asset no encontrado: " + from);
        if (children.length > 0) {
            if (!out.exists() && !out.mkdirs()) throw new IOException("No se puede crear " + out);
            for (String child : children) copyRecursive(a, from + "/" + child, new File(out, child));
        } else {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new IOException("No se puede crear " + parent);
            try (InputStream src = a.open(from); FileOutputStream dst = new FileOutputStream(out)) {
                byte[] bytes = new byte[65536]; int count;
                while ((count = src.read(bytes)) != -1) dst.write(bytes, 0, count);
            }
        }
    }
}


