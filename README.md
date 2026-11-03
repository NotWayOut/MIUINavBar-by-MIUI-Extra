# MIUINavBar

基于 **libxposed API 101** 的 MIUI / HyperOS 小白条（手势栏）增强模块。

本项目从 [MIUI-Extra-YukiAPI](https://github.com/Art-Chen/MIUI-Extra-YukiAPI)（MIUI Extra，作者 [Art_Chen](https://github.com/Art-Chen)）中**提取了小白条相关的全部功能**，并进行了彻底重写：

- Hook 层由 YukiHookAPI 迁移为 **libxposed API 101 原生写法**（`hook(executable).intercept { chain -> ... }`，无 `XposedHelpers` 封装）
- 界面由 PreferenceFragment 迁移为 **miuix（Compose Multiplatform）** 风格

## 适配范围

os1 **~** os3a15

## 功能

- **总开关**：一键启用/禁用全部小白条功能，未开启时下方选项不可用
- **沉浸式手势栏**：手势栏不再占位（通过修改 insetSize 实现，可能引入布局错误，慎用）
- **自定义小白条状态**：分别配置「默认时 / 按下时 / 在桌面时」三组参数
  - 自动透明程度、横向缩放、纵向缩放、X/Y 轴偏移
  - 透明（模糊）动画时长、缩放动画时长、位移动画时长
- **模糊材质**：在小白条上应用 MIUI 高级材质模糊（需系统开启高级材质）
- **禁用跟手位移**：小白条不再跟随手指拖动位移
- **在桌面时强制使用透明度调整**：桌面下用透明度动画替代模糊级别调整，并去除模糊材质
- **配置备份 / 恢复**：支持miuiextra的备份文件，只识别已迁移功能（JSON 格式，导入限制 `.json` 后缀）

> 原模块中的「小白条动画增强模式」与「手势栏隐藏时隐藏小白条」未做迁移。

## 环境要求

| 项目 | 要求 |
|---|---|
| 框架 | 支持 libxposed API 101 的 Xposed 框架（如 LSPosed） |
| 系统 | MIUI / HyperOS，Android 13（API 33）及以上 |
| 权限 | Root（用于重启系统界面；小白条功能本身不依赖 Root） |
| 作用域 | `com.android.systemui` |

## 使用

1. 在 Xposed 管理器中激活 **MIUINavBar**，确认作用域包含 `com.android.systemui`
2. 重启系统界面（模块内提供一键重启，或 `su -c killall com.android.systemui`）
3. 打开模块，开启「总开关」并按需调整各项参数
4. 修改参数后小白条动画即时生效；涉及结构的变化（如沉浸式手势栏）建议重启系统界面

## 编译

```bash
# Debug
./gradlew assembleDebug

# Release（R8 混淆 + 资源压缩）
.\gradlew :app:assembleRelease -PversionCode=100 -PversionName="1.0.0"
```

默认值在 `app/build.gradle.kts` 中，也可在 `gradle.properties` 中取消注释 `versionCode` / `versionName` 长期覆盖。

签名配置从 `local.properties` 读取

### 引用与致谢

- **[Art_Chen](https://github.com/Art-Chen)** — 原项目 [MIUI-Extra-YukiAPI](https://github.com/Art-Chen/MIUI-Extra-YukiAPI)（MIUI Extra）的作者，小白条动画、沉浸式手势栏、自动透明度等功能的原始实现均来自该项目；喜欢他的作品可以 [请作者喝杯咖啡](https://afdian.com/a/art_chen)
- **[Android Open Source Project](https://cs.android.com)** — Google.
- **[libxposed](https://github.com/libxposed)** — 提供 API 101 及官方示例模块的 Hook 写法参考
- **[LSPosed](https://github.com/LSPosed/LSPosed)** — 提供远程首选项与模块服务框架
- **[miuix](https://github.com/compose-miuix-ui/miuix)**（Apache-2.0）— Compose Multiplatform 风格 UI 组件库
- **[YukiHookAPI](https://github.com/HighCapable/YukiHookAPI)**（fankes）— 原项目使用的 Hook 框架，本项目的迁移逻辑参考了其封装的行为

## 许可证

本项目遵循上游项目的 **AGPL-3.0** 许可证，详见 [LICENSE](LICENSE)。

使用或转载代码时请遵守相关开源许可证条款。
