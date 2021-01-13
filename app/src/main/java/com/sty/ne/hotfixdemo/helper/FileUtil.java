package com.sty.ne.hotfixdemo.helper;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @Author: tian
 * @UpdateDate: 2021/1/11 9:33 PM
 */
public class FileUtil {
    private static final String TAG = FileUtil.class.getSimpleName();
    private static final String CODE_CACHE_NAME = ".opt_dir";

    public static File getDexOptDir(Context context) throws IOException {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            return null;
        }

        File cache = new File(applicationInfo.dataDir, CODE_CACHE_NAME);
        try {
            makeAndEnsureDirExisted(cache);
        } catch (IOException e) {
            /* If we can't emulate code_cache, then store to filesDir. This means abandoning useless
             * files on disk if the device ever updates to android 5+. But since this seems to
             * happen only on some devices running android 2, this should cause no pollution.
             */
            cache = new File(context.getFilesDir(), CODE_CACHE_NAME);
            makeAndEnsureDirExisted(cache);
        }
        return cache;
    }

    public static void makeAndEnsureDirExisted(File dir) throws IOException {
        if (dir.isDirectory() && dir.exists()) {
            return;
        }
        dir.mkdir();
        if (!dir.isDirectory()) {
            File parent = dir.getParentFile();
            if (parent == null) {
                Log.e(TAG, "Failed to create dir " + dir.getPath() + ". Parent file is null.");
            } else {
                Log.e(TAG, "Failed to create dir " + dir.getPath() +
                        ". parent file is a dir " + parent.isDirectory() +
                        ", a file " + parent.isFile() +
                        ", exists " + parent.exists() +
                        ", readable " + parent.canRead() +
                        ", writable " + parent.canWrite());
            }
            throw new IOException("Failed to create directory " + dir.getPath());
        }
    }

    public static boolean copyAsset2Dst(Context context, String assetName, File patch) {
        boolean bSuccess = false;
        InputStream in = null;
        OutputStream out = null;
        try {
            AssetManager assetManager = context.getAssets();
            in = assetManager.open(assetName);
            int available = in.available();
            out = new FileOutputStream(patch);
            int byteCopy = copyFile(in, out);
            if (byteCopy == available) {
                Log.d(TAG, "copyAsset success");
                bSuccess = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bSuccess;
    }

    private static int copyFile(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        return total;
    }
}
