

# Tinker热修复原理

[TOC]

## 一、热修复原理

### 1.1 热修复产生的背景及其意义

* 传统修复`bug`流程：版本升级率低，需要较长时间完成新版本覆盖；
* 热修复修复`bug`流程：无发版，用户无感知。

![image](https://github.com/tianyalu/NeHotFixDemo/raw/master/show/hot_fix_background.png)

### 1.2 业界热修复流派

* **基于`MultiDex`的`Dex`注入**：代表有`Tinker`，手`Q`空间，`Nuwa`等；原理是将补丁`Dex`对应的`DexFile`对象注入到系统`ClassLoader`相关联的`DexPathList`对象的`dexElements`数组的最前面。
* **`Native`层方法替换**：代表有`AndFix`，阿里百川`HotFix`等；原理是在`Native`层对方法的整体数据结构（`Method/ArtMethod`）进行替换。
* **`ClassLoader Hack`**：代表`Instant Run`；原理是基于双亲委派机制，用自定义的`IncrementalClassLoader`加载补丁`Dex`，同时将该类加载器设置为系统类加载器的父加载器。

本质：类替换，粒度不同（类所在`Dex`，类方法，类加载器）。

### 1.3 类加载机制

#### 1.3.1 `Java`类加载流程

* **加载**：遇到需要触发加载类的指令或场景时执行；
* **链接**：大体上包含以下三个步骤，①验证(`Verify`)：验证字节码的合法性与安全性；②准备(`Prepare`)：为类变量分配内存并设置变量初始值；③解析(`Resolve`)：将常量池中的符号引用替换为直接引用；
* **初始化**：执行构造器`<clinit>`。

#### 1.3.2 `Android`中类加载流程

大体上与`Java`类加载流程一致

```java
dvmLinkClass --> dvmResolveClass(可能会出现类校验安全问题) --> dvmVerifyClass --> dvmOptimizeClass --> dvmInitClass
```

* **多对一映射**：多个类位于同一个`Dex`文件中；
* **验证(`Verify`)与解析(`Resolve`)前置**：执行`dexopt`时进行`Verification`与`Optimization`；
* **运行时再次校验**：类初始化时会检测该类是否已经被校验过，如果为校验过将进行校验。

### 1.4 `Tinker`热修复原理

#### 1.4.1 `Dex`插桩原理

`ClassLoader`的加载路径可以包含多个`dex`文件，每个`dex`文件关联一个`Element`，多个`dex`文件排列成一个有序的数组`dexElements`，当查找类的时候会按顺序遍历`dex`文件，然后从当前遍历的`dex`文件中找类，如果找到则返回，如果找不到从下一个`dex`文件继续查找。理论上如果在不同的`dex`中有相同的类存在，那么会优先选择排在前面的`dex`文件中的类。

![image](https://github.com/tianyalu/NeHotFixDemo/raw/master/show/tinker_hot_fix_theory.png)

#### 1.4.2 类校验异常原理

热修复就是要破坏其中的某一个条件：

![image](https://github.com/tianyalu/NeHotFixDemo/raw/master/show/class_verify_exception_theory.png)

#### 1.4.3 伪代码实现流程（仅提供思路，实操有问题）

* 获取系统`ClassLoader`的`pathList`对象

```java
Object pathList = Reflect.on(loader).field("pathList").get();
```

* 调用`makePathElements`构造补丁的`dexElements`

```java
ArrayList<IOException> suppressedExceptions = new ArrayList<>();
Object[] patchDexElements = makePathElements(pathList, extraDexFiles, FileUtil.getDexOptDir(context), suppressedExceptions);
```

* 将补丁`Dex`注入到系统`ClassLoader`的`pathList`对象的`dexElements`的最前面

```java
expandElementsArray(pathList, patchDexElements);
```

## 二、实操

### 2.1 实现效果

实现效果如下图所示：

![image](https://github.com/tianyalu/NeHotFixDemo/raw/master/show/show.gif)

### 2.2 实现步骤

#### 2.2.1 操作步骤

* `hotfix` 的模块创建与主项目完全相同的包名，相同的路径创建`Sample.java`文件，作为模拟热修复的文件。
* 打包`hotfix`项目，获取包含`Sample`类的`dex`文件，放置到主项目的`assets`目录下，模拟网络下载热修复文件。
* 主项目在点击“加载补丁”按钮后从`assets`目录下复制`bug-fix.dex`文件，并通过反射将补丁`dex`文件插入到原`dexElements`数组前面。

#### 2.2.2 核心代码

加载补丁包：

```java
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
```

注入补丁包`dex`：

```java
    public static void tryInjectDex(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile != null && patchFile.exists()) {
            try {
                injectDex(context, context.getClassLoader(), patchFile);
                Log.d(TAG, "inject dex success!");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "inject dex failed:" + e.toString());
            }
        }
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
```

删除补丁包：

```java
    public static boolean deletePatchFile(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        return patchFile.delete();
    }
```

