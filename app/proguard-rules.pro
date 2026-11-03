# libxposed API 模块入口类保留规则（R8 会重写 META-INF/xposed/java_init.list）
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# androidx.window 为部分 androidx 库的可选依赖，R8 需要忽略缺失类
-dontwarn androidx.window.**
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.core.**
