package com.sty.ne.hotfixdemo.helper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * @Author: tian
 * @UpdateDate: 2021/1/11 9:38 PM
 */
public class HotFixHelper {
    private static final String TAG = HotFixHelper.class.getSimpleName();
    private static final String PATCH_DIR = "patch_dir";

    public static boolean loadPatch(Context context, String assetName) {
        File patchDir = new File(context.getFilesDir(), PATCH_DIR);
        try {
            FileUtil.makeAndEnsureDirExisted(patchDir);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "create patch dir failed");
            return false;
        }
        File patchFile = new File(patchDir, assetName);
        return FileUtil.copyAsset2Dst(context, assetName, patchFile);
    }

    public static void tryInjectDex(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile != null && patchFile.exists()) {
//            ArrayList<File> files = new ArrayList<>();
//            files.add(patchFile);
            try {
                injectDex(context, context.getClassLoader(), patchFile);
                Log.d(TAG, "inject dex success!");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "inject dex failed:" + e.toString());
            }
        }
    }

    public static boolean deletePatchFile(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        return patchFile.delete();
    }

    private static void injectDex(Context context, ClassLoader loader, File patchFile)
            throws NoSuchFieldException, IllegalAccessException {
        // 获取获取当前正在运行的apk中的系统ClassLoader的pathList对象
        //Object pathList = Reflect.on(loader).field("pathList").get();

        ClassLoader pathClassLoaderClass = context.getClassLoader();
        Class baseDexClassLoaderClass = BaseDexClassLoader.class;
        Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(pathClassLoaderClass);

        //通过pathList获取原应用的dexElements
        Class pathListClass = pathList.getClass();
        Field dexElementsField = pathListClass.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        Object dexElementsObject = dexElementsField.get(pathList);

        // 获取补丁的dexElements
        PathClassLoader newClassLoader = new PathClassLoader(patchFile.getPath(), loader.getParent());
        Log.e("sty", "pathFile: " + patchFile.getPath());
        Object newPathListObject = pathListField.get(newClassLoader);
        Object extraDexElementsObject = dexElementsField.get(newPathListObject);

        // 将补丁Dex注入到系统ClassLoader的pathList对象的dexElements的最前面
        Object expandElementsArray = expandElementsArray(pathList, dexElementsObject, extraDexElementsObject);
        dexElementsField.set(pathList, expandElementsArray);
    }

    /**
     * 用我们自己生成的dexElements对象来替换掉当前应用的dexElements对象，其中自己生成的是包含当前的和要修复的dexElements
     * @param pathList
     * @param originalElements
     * @param extraElements
     */
    private static Object expandElementsArray(Object pathList, Object originalElements, Object extraElements) {
        int oldLength = Array.getLength(originalElements);
        int extraLength = Array.getLength(extraElements);
        Object concatDexElementsObject = Array.newInstance(originalElements.getClass().getComponentType(), oldLength + extraLength);

        for (int i = 0; i < extraLength; i++) {
            Array.set(concatDexElementsObject, i, Array.get(extraElements, i));
        }
        for (int i = 0; i < oldLength; i++) {
            Array.set(concatDexElementsObject, extraLength + i, Array.get(originalElements, i));
        }

        Log.e("sty", "length: " + oldLength + " " + extraLength + Array.getLength(concatDexElementsObject));

        return concatDexElementsObject;
    }
}
