# GTLite 附属模组开发环境 · 开发说明

工程：`D:\study\mcmod_dev`（CleanroomMC **TemplateDevEnv**，MC 1.12.2，RetroFuturaGradle 2.0.3）
用途：给 GregTech Lite 写附属。**当前阶段只做环境与依赖配置，不含业务代码。**

---

## 1. 版本矩阵

| 组件 | 版本 | 引入方式 |
|---|---|---|
| Minecraft | 1.12.2 | RFG `minecraft.mcVersion` |
| **Forge** | **14.23.5.2847**（目标 2860 无法实现，见 §1.1） | RFG 固定 |
| Gradle | 9.7.0 | wrapper `gradle/wrapper/gradle-wrapper.properties` |
| **Gradle JVM** | **JDK 25**（Temurin 25.0.4.1+1，`D:\study\java\jdk25`） | `JAVA_HOME` / IDEA `Gradle JVM` |
| 编译 toolchain | **Zulu 8**（Gradle 自动下载 `~/.gradle/jdks/azul_systems__inc_-8-*`） | `java.toolchain.languageVersion = 8` |
| JST 工具 toolchain | Zulu 21（`applyJST` 用，自动下载） | RFG 内部 |
| MCP 映射 | `stable` / `39` | `gradle.properties` |
| GTCEu | master 构建 **#2901**（mcmod.info `2.8.7-beta`） | **本地 jar** `libs/gregtech-1.12.2-master-2901.jar` |
| GregTech Lite Core | **1.0.0** | **本地 jar** `libs/gtlitecore-1.0.0.jar` |
| Morphism Lib | **1.0.0** | **本地 jar** `libs/morphismlib-1.12.2-1.0.0.jar` |
| Forgelin Continuous | **2.2.20.0** | CurseMaven `456403:6983420` |
| ModularUI | **3.0.6** | CurseMaven `624243:7320544` |
| MixinBooter | **10.7** | CurseMaven `419286:7049694` |
| CodeChickenLib | **1.12.2-3.2.3.358** | CurseMaven `242818:2779848` |
| AE2 Unofficial Extended Life | **0.56.7** | CurseMaven `570458:6302098` |
| Had Enough Items（modid `jei`） | **4.36.1** | CurseMaven `557549:9167450` |

> 版本来源：整合包 [GregTechLite/GregTech-Lite-Modpack](https://github.com/GregTechLite/GregTech-Lite-Modpack)
> 的 `pack.toml` + `mods/*.pw.toml` + `mods/` 内随包 jar。**不要凭印象改版本**，改之前先去仓库确认。

### 1.1 为什么 Forge 停在 2847，而不是整合包的 2860

整合包锁定 `forge = 14.23.5.2860`，但**这个 DevEnv 无法对齐到 2860**，两条独立证据：

1. **RetroFuturaGradle 2.0.3 写死只支持 2847。**
   `IMinecraftyExtension#getForgeVersion()` 的映射表只有两项：`1.7.10 → 10.13.4.1614-1.7.10`、`1.12.2 → 1.12.2-14.23.5.2847`，
   其它输入直接 `UnsupportedOperationException`。把 `mcVersion` 改成 `1.12.2-14.23.5.2860` 的实测结果是：

   ```
   A problem occurred configuring root project 'mcmod_dev'.
   > Unsupported MC version 1.12.2-14.23.5.2860
   ```

2. **Forge 官方根本没给 2860 发布 userdev 包。**
   `maven-metadata.xml` 里 2860 这个版本存在，但制品只有 `universal.jar` / `installer.jar`：

   | 制品 | 2847 | 2860 |
   |---|---|---|
   | `forge-<ver>-userdev.jar`（maven.minecraftforge.net） | **200** | **404** |
   | 同上（`/releases/` 路径，即 files.minecraftforge.net 301 的落点） | **200** | **404** |
   | 同上（bmclapi `…/maven/` 镜像） | 200 | 404 |
   | 同上（maven.cleanroommc.com 镜像） | 200 | 404 |

   RFG 只能从 userdev 包构建 patched MC（`RfgCacheService#accessForgeUserdev`），没有 userdev 就无从下手。

**结论**：编译期 Forge 固定 2847；运行期放在 2860 客户端/服务端上没有问题（Forge 向后兼容，且模板默认带了
`strip-latest-forge-requirements` 绕过 mod 里的 Forge 版本下限检查）。如果你的附属硬依赖 2860 才有的补丁，
那只能换用支持自定义 forge 版本的构建工具链（超出本模板范围）。

---

## 2. 依赖引入方式

全部改动集中在 `gradle/scripts/dependencies.gradle`（**build.gradle / gradle.properties / wrapper 不动**）。

* **本地 jar**（CurseForge 上没有、或必须与整合包同源的）：放 `libs/`，用
  ```groovy
  implementation rfg.deobf(files('libs/gtlitecore-1.0.0.jar'))
  ```
  文件名、来源、大小、SHA-256 见 **[libs/SOURCES.md](libs/SOURCES.md)**；`libs/*.jar` 已被 `.gitignore` 忽略。
* **CurseMaven**（整合包 pw.toml 里给了 CurseForge file-id 的）：
  ```groovy
  implementation rfg.deobf('curse.maven:forgelin-continuous-456403:6983420')
  ```
  仓库走 `curse.cleanroommc.com`（模板已在 `dependencies.gradle` 里 `exclusiveContent` 配置好）。
* 所有 mod 依赖都用 `rfg.deobf(...)` 包一层，否则 1.12.2 的混淆名在 dev 环境里会 `NoSuchMethodError`。

`libs/` 三个 jar**不在版本库**里，新机器拉下来后要按 SOURCES.md 重新放回，否则 `build` 会在 `extractDependencyATs` 阶段报
「Could not resolve all files for configuration ':compileClasspath'」。

---

## 3. 三个运行时坑（都已实测踩过，按顺序记录）

1. **AE2 是隐式必需，不是可选联动。**
   GTLite Core 的 `mixins.gtlitecore.gregtech.json:MixinMetaTileEntityHolder` 引用了
   `appeng.api.networking.crafting.ICraftingProvider`。缺 AE2 时 Mixin 预处理直接失败，
   连锁导致 GTCEu 的 `MetaTileEntityHolder` 类加载不出来：

   ```
   MixinPreProcessorException: Attach error for ...:MixinMetaTileEntityHolder
     Caused by: ClassNotFoundException: appeng.api.networking.crafting.ICraftingProvider
   LoaderExceptionModCrash: Caught exception from GregTech (gregtech)
     Caused by: NoClassDefFoundError: gregtech/api/metatileentity/MetaTileEntityHolder
   ```

2. **JEI（本次用 HEI 4.36.1）也是隐式必需。**
   `MCPotions` 的静态初始化会走到 `mezz.jei.plugins.vanilla.brewing.BrewingRecipeUtil`，
   没有 JEI 就在注册配方阶段崩客户端：

   ```
   NoClassDefFoundError: mezz/jei/plugins/vanilla/brewing/BrewingRecipeUtil
     at gregtechlite.gtlitecore.api.item.MCPotions.potion(MCPotions.kt:84)
     at gregtechlite.gtlitecore.loader.recipe.foodprocessing.MilkProcessing.init(MilkProcessing.kt:63)
   ```

3. **本地 jar 文件名不能含 `#`。**
   仓库原文件名 `gregtech-1.12.2-master-#2901.jar` 会让 RFG 的 deobf transform 失败：

   ```
   Execution failed for DependencyDeobfuscationTransform: .../libs/gregtech-1.12.2-master-#2901.jar
   > Error encountered when deobfuscating ...
   ```

   已重命名为 `gregtech-1.12.2-master-2901.jar`。

另外两条容易忽略的：**GTCEu 必须用随包 #2901，CurseForge 的 `5121638` 不可用**（缺 `MTEManager$MTERegistryEvent` 等类）；
**Forgelin 以整合包的 2.2.20.0 为准**，不要用 GTLite Core 自己开发环境里 pin 的 2.1.0.0（README 只要求「hard depend」，
版本跟整合包对齐才不会在运行期遇到 Kotlin 元数据版本差异）。

---

## 4. IDEA 设置

1. `File > Open` 选 `D:\study\mcmod_dev`（它已有 `.idea` 与 gradle 工程，直接以 Gradle 项目导入）。
2. `Settings > Build, Execution, Deployment > Build Tools > Gradle`
   * **Gradle JVM：选 JDK 25**（`D:\study\java\jdk25`）。**不要选 8** —— Gradle 9.7.0 的运行 JVM 要求 ≥ 17，
     选 8 会在 sync 阶段直接失败。
   * 语言级别 / 编译用 JDK 由模板 toolchain 自动处理（Zulu 8），不需要手动把 Project SDK 改到 8。
3. 首次 sync 后会生成 5 个运行配置（模板 `idea-ext` 注册）：
   `0. Setup Decomp Workspace` / `1. Run Client` / `2. Run Server` / `3. Run Obfuscated Client` / `4. Run Obfuscated Server`。
   日常用 `1. Run Client`。首次启动前先跑一次 `downloadVanillaAssets`（或直接等 `runClient` 自己下，约 200 MB）。
4. 命令行等价：
   ```bash
   export JAVA_HOME="D:/study/java/jdk25"
   ./gradlew build
   ./gradlew runClient
   ```

---

## 5. API 入口

GTLite Core 的可用 API（方块等级样板系统、RecipeBuilder 扩展、MCPotions、Meta 物品渲染、Mixin 配置结构）
已按 jar 字节码逐个 `javap` 核对，见 **[docs/GTLITE_CORE_API.md](GTLITE_CORE_API.md)**。
写代码前先看那份清单，需要的类直接 `Ctrl+Click` 进 deobf 后的依赖即可，不要凭记忆写签名。

---

## 6. 当前状态

* `./gradlew build`：**SUCCESSFUL**（产物 `build/libs/modid-1.0.0.jar`）。
* `./gradlew runClient`：**SUCCESSFUL**，`Forge Mod Loader has successfully loaded 15 mods`，
  GTLite Core 的方块/物品/配方注册完成，可正常进世界（日志 `run/logs/latest.log`）。
* `src/main/java` 里仍只有模板自带的 `com.example.modid.ExampleMod`，**未写任何业务代码**。
