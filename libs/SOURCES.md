# libs/ 本地依赖清单

`libs/*.jar` 不进版本库（`.gitignore` 已忽略），但编译与 `runClient` 必需。
版本严格对齐整合包 [GregTechLite/GregTech-Lite-Modpack](https://github.com/GregTechLite/GregTech-Lite-Modpack)
（`pack.toml`：Minecraft 1.12.2 + Forge 14.23.5.2860，packwiz 格式）。

## 两条硬约束（改这个目录之前必读）

1. **GTCEu 必须用整合包随包的 master-#2901 自编译构建，不能用 CurseForge 的 file id 5121638。**
   CurseForge 上的 `gregtech-1.12.2-2.8.7-beta.jar`（project 557242 / file 5121638）版本号同样是 `2.8.7-beta`，
   但比 #2901 旧，缺少 `gregtech/api/metatileentity/registry/MTEManager$MTERegistryEvent`、
   `gregtech/api/metatileentity/multiblock/ui/MultiblockUIFactory`、`MultiblockUIBuilder`、`gregtech/api/capability/DualHandler` 等类
   —— GTLite Core 1.0.0 就是对着 #2901 编译的。用 CF 版本的实测结果是：

   ```
   LoaderExceptionModCrash: Caught exception from GregTech Lite Core (gtlitecore)
   Caused by: java.lang.NoClassDefFoundError: gregtech/api/metatileentity/registry/MTEManager$MTERegistryEvent
   ```

2. **jar 文件名里不能有 `#`。**
   仓库里的原文件名是 `gregtech-1.12.2-master-#2901.jar`；`#` 在 URI 中是 fragment 分隔符，
   RFG 的 deobf transform 会直接失败：

   ```
   Execution failed for DependencyDeobfuscationTransform: .../libs/gregtech-1.12.2-master-#2901.jar
   > Error encountered when deobfuscating ...
   ```

   所以本地重命名为 `gregtech-1.12.2-master-2901.jar`，`gradle/scripts/dependencies.gradle` 引用的是重命名后的名字。

## 文件清单

| 文件名 | 版本 | 来源（相对 `mods/`） | 大小（字节） | SHA-256 |
|---|---|---|---|---|
| `gregtech-1.12.2-master-2901.jar` | GTCEu master 构建 #2901（`mcmod.info` 版本号 `2.8.7-beta`） | `mods/gregtech-1.12.2-master-%232901.jar`（URL 中 `#` 编码为 `%23`） | 14,054,011 | `b9fea8ac4599f04348c12611e515d43dc15819a9b11df9bfbc1495e7e123b317` |
| `gtlitecore-1.0.0.jar` | GregTech Lite Core 1.0.0（CurseForge 未发布） | `mods/gtlitecore-1.0.0.jar` | 14,953,638 | `d79e244e8b8d2769637e5fa2908bcb3c944b04846dc8496a9dc75fbda1d6cef6` |
| `morphismlib-1.12.2-1.0.0.jar` | Morphism Lib 1.0.0（GTLite Core 的渲染依赖） | `mods/morphismlib-1.12.2-1.0.0.jar` | 139,150 | `39e6271853929d13a73e62dea72bb13392bf3c35734b37d518fc5d98bba3b233` |

下载基址：`https://raw.githubusercontent.com/GregTechLite/GregTech-Lite-Modpack/main/mods/`

原始下载 URL（可直接重放）：

```
https://raw.githubusercontent.com/GregTechLite/GregTech-Lite-Modpack/main/mods/gregtech-1.12.2-master-%232901.jar
https://raw.githubusercontent.com/GregTechLite/GregTech-Lite-Modpack/main/mods/gtlitecore-1.0.0.jar
https://raw.githubusercontent.com/GregTechLite/GregTech-Lite-Modpack/main/mods/morphismlib-1.12.2-1.0.0.jar
```

> 下载后第 1 个文件请重命名去掉 `#`（`mv gregtech-1.12.2-master-%232901.jar gregtech-1.12.2-master-2901.jar`）。

## 校验

```bash
sha256sum libs/*.jar
# 每个 jar 都应为完整 zip（无 "End-of-central-directory signature not found"）
for j in libs/*.jar; do unzip -t "$j" >/dev/null && echo "OK $j"; done
```

## 其余依赖（不在 libs/，走 CurseMaven）

见 `gradle/scripts/dependencies.gradle`：AE2 UEL 0.56.7、Had Enough Items 4.36.1、Forgelin-Continuous 2.2.20.0、
ModularUI 3.0.6、MixinBooter 10.7、CodeChickenLib 3.2.3.358。
