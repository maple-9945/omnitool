# GTLite Core 1.0.0 API 切入点

来源：`libs/gtlitecore-1.0.0.jar`（SHA-256 `d79e244e…d6cef6`）实际字节码，用
`javap -p -cp <解压目录> <全限定名>` 取得，未做任何推测。**标「未找到」的条目就是在 jar 里确实不存在。**

GTLite Core 是 Kotlin 1.9+ 工程，Java 侧调用时注意三条：

* Kotlin `object` → Java 用 `Xxx.INSTANCE`；
* Kotlin 顶层函数（`Xxx.kt`）→ Java 用 `XxxKt` 类 + `public static` 方法；
* 扩展函数（如 `RecipeBuilder.recipeDuration(...)`）→ Java 里就是「接收者当第一个参数」的静态方法。

---

## 1. Template Block Tier Registries（带等级的方块样板系统）

「样板方块」= `BlockVariant` 枚举 + `VariantBlockFactory`（六种模板类型）；
「等级」= `StateTier` + `BlockAttributeRegistry`（按 `IBlockState` 查等级/排序）。

| 全限定名 | 类别 | jar 内路径 |
|---|---|---|
| `gregtechlite.gtlitecore.api.block.variant.BlockVariant` | 接口：样板枚举必须实现 | `gregtechlite/gtlitecore/api/block/variant/BlockVariant.class` |
| `gregtechlite.gtlitecore.api.block.variant.BlockVariantType` | 枚举：六种模板 | `…/api/block/variant/BlockVariantType.class` |
| `gregtechlite.gtlitecore.api.block.variant.VariantBlockFactory` | 工厂（Kotlin object） | `…/api/block/variant/VariantBlockFactory.class` |
| `gregtechlite.gtlitecore.api.block.variant.types.MetalBlockKt` 等 | 各类型顶层扩展 | `…/api/block/variant/types/*.class` |
| `gregtechlite.gtlitecore.api.block.attribute.StateTier` | 接口：`getTier()` | `…/api/block/attribute/StateTier.class` |
| `gregtechlite.gtlitecore.api.block.attribute.BlockAttributeRegistry` | 接口：属性登记表 | `…/api/block/attribute/BlockAttributeRegistry.class` |
| `gregtechlite.gtlitecore.api.block.attribute.DefaultBlockAttributeRegistry` | 实现（Comparator 排序） | `…/api/block/attribute/DefaultBlockAttributeRegistry.class` |
| `gregtechlite.gtlitecore.api.block.attribute.BlockAttributeRegistryWrapper` | 实现（包一个 Map） | `…/api/block/attribute/BlockAttributeRegistryWrapper.class` |
| `gregtechlite.gtlitecore.api.block.attribute.BlockAttributeRegistryKt` | 批量注册顶层函数 | `…/api/block/attribute/BlockAttributeRegistryKt.class` |

关键签名：

```java
// BlockVariant
IBlockState getState();
ItemStack  getStack();
ItemStack  getStack(int amount);

// BlockVariantType（enum 常量）
METAL_BLOCK, METAL_CUTOUT_BLOCK, METAL_ACTIVE_BLOCK,
WIRE_COIL_BLOCK, TRANSPARENT_BLOCK, CRUCIBLE_BLOCK

// VariantBlockFactory
<T extends Enum<T> & IStringSerializable> VariantBlock<T> make(BlockVariantType type);

// 各类型入口（Kotlin 顶层函数 → Java 静态方法）
<T extends Enum<T> & IStringSerializable> VariantBlock<T> makeMetalBlock(VariantBlockFactory);
<T extends Enum<T> & IStringSerializable> VariantActiveBlock<T> makeMetalActiveBlock(VariantBlockFactory);
<T …> VariantBlock<T> makeMetalCutoutBlock(VariantBlockFactory);
<T …> VariantBlock<T> makeTransparentBlock(VariantBlockFactory);
<T …> VariantBlock<T> makeWireCoilBlock(VariantBlockFactory);
<T …> VariantBlock<T> makeCrucibleBlock(VariantBlockFactory);

// StateTier
int getTier();

// BlockAttributeRegistry<T>
String getName();
List<IBlockState> getAscendingBlocks();   // 按等级升序
List<IBlockState> getDescendingBlocks();  // 按等级降序
int getBlockSize();
int getAttributeSize();
void register(IBlockState state, T attribute);
void registerAll(Pair<IBlockState, T>... pairs);   // Kotlin Pair
void unregister(IBlockState state);
T getAttribute(IBlockState state);
List<IBlockState> getBlock(T attribute);

// DefaultBlockAttributeRegistry<T> / BlockAttributeRegistryWrapper<T>
DefaultBlockAttributeRegistry(String name, Comparator<? super T> comparator);
BlockAttributeRegistryWrapper(String name, Map<IBlockState, T> map, Comparator<? super T> comparator);

// BlockAttributeRegistryKt（KClass 需用 JvmClassMappingKt.getKotlinClass(MyEnum.class) 转换）
<T extends Enum<T> & BlockVariant>                void registerBlockVariants(BlockAttributeRegistry<? super T> registry, KClass<T> klass);
<T extends Enum<T> & BlockVariant & StateTier>    void registerBlockVariantsTiered(BlockAttributeRegistry<Integer> registry, KClass<T> klass);
<V extends Enum<V> & BlockVariant, T>             void registerBlockVariants(BlockAttributeRegistry<? super T> registry, KClass<V> klass, Function1<V, T> mapper);
```

> `registerBlockVariantsTiered` 的泛型约束是关键：**你的样板枚举要同时 `implements BlockVariant, StateTier`**，注册表类型是 `BlockAttributeRegistry<Integer>`。
> 具体使用范例在 jar 内 `gregtechlite/gtlitecore/loader/` 下（`getBlock`/`getStack` 调用点），如需可再用 `javap` 反查。

---

## 2. RecipeBuilder 扩展（配方构建器与属性扩展）

| 全限定名 | 作用 | jar 内路径 |
|---|---|---|
| `gregtechlite.gtlitecore.api.extension.RecipeBuilderExtKt` | `RecipeBuilder` 的 Kotlin 扩展函数集合 | `…/api/extension/RecipeBuilderExtKt.class` |
| `gregtechlite.gtlitecore.api.recipe.GTLiteRecipeProperties` | 自定义 `RecipeProperty<Integer>` | `…/api/recipe/GTLiteRecipeProperties.class` |
| `gregtechlite.gtlitecore.api.recipe.GTLiteRecipeMaps` | 64 个自定义 `RecipeMap` 常量 | `…/api/recipe/GTLiteRecipeMaps.class` |
| `gregtechlite.gtlitecore.api.recipe.GTLiteRecipeCategories` | 2 个 `GTRecipeCategory` | `…/api/recipe/GTLiteRecipeCategories.class` |
| `gregtechlite.gtlitecore.api.recipe.builder.*` | 14 个专用 builder | `…/api/recipe/builder/*.class` |
| `gregtechlite.gtlitecore.mixins.gregtech.MixinRecipeBuilder` | 给 GTCEu `RecipeBuilder` 注入 `requestEUtRange` 校验 | `…/mixins/gregtech/MixinRecipeBuilder.class` |

`RecipeBuilderExtKt` 的全部公开签名：

```java
static <T extends RecipeBuilder<T>> void addRecipe(RecipeMap<T> map, Function1<T, Unit> action);
static <T extends RecipeBuilder<T>> T inputs(T b, Item item, int amount, int meta);
static <T extends RecipeBuilder<T>> T inputs(T b, Block block, int amount, int meta);
static <T extends RecipeBuilder<T>> T outputs(T b, Item item, int amount, int meta);
static <T extends RecipeBuilder<T>> T outputs(T b, Block block, int amount, int meta);
static <T extends RecipeBuilder<T>> T EUt(T b, int eut);
static <T extends ResearchRecipeBuilder<T>> T EUt(T b, int eut);       // 重载：研究型配方
static <T extends RecipeBuilder<T>> T duration(T b, long ticks);
static <T extends RecipeBuilder<T>> T duration(T b, double seconds);
static <T extends RecipeBuilder<T>> T cleanroom(T b);
static <T extends RecipeBuilder<T>> T sterileCleanroom(T b);
static <T extends RecipeBuilder<T>> boolean removeRecipe(RecipeMap<T> map, ItemStack... inputs);
static <T extends RecipeBuilder<T>> boolean removeRecipe(RecipeMap<T> map, FluidStack... inputs);
static <T extends RecipeBuilder<T>> boolean removeRecipe(RecipeMap<T> map, ItemStack[] items, FluidStack[] fluids);
static <T extends RecipeBuilder<T>> boolean removeRecipe(RecipeMap<T> map, Item... items);
```

Java 调用形态（Kotlin 扩展 → 静态方法）：

```java
RecipeBuilderExtKt.addRecipe(GTLiteRecipeMaps.POLISHER_RECIPES, b -> {
    RecipeBuilderExtKt.inputs(b, Items.DIAMOND, 1, 0);
    RecipeBuilderExtKt.duration(b, 200);
    RecipeBuilderExtKt.EUt(b, 480);
    return Unit.INSTANCE;
});
```

`GTLiteRecipeProperties`（`INSTANCE` + 6 个 `public static RecipeProperty<Integer>`）：

```
ACCELERATION_TRACK_TIER, ADVANCED_FUSION_TIER, COMPONENT_ASSEMBLY_LINE_TIER,
MINIMUM_HEIGHT, NO_COIL_TEMPERATURE, QUANTUM_FORCE_TRANSFORMER_TIER
```

`GTLiteRecipeMaps`：**64 个 `public static final RecipeMap<…>`**，例如
`POLISHER_RECIPES`、`SLICER_RECIPES`、`TOOL_CASTER_RECIPES`、`LOOM_RECIPES`、`LAMINATOR_RECIPES`、
`CHEMICAL_DEHYDRATOR_RECIPES`、`VULCANIZATION_RECIPES`、`VACUUM_CHAMBER_RECIPES`、`SAP_COLLECTOR_RECIPES`（`PseudoMultiRecipeBuilder`）、
`ADVANCED_FUSION_RECIPES`、`QUANTUM_FORCE_TRANSFORMER_RECIPES`、`PCB_FACTORY_RECIPES`、`NANO_FORGE_RECIPES`、
`COMPONENT_ASSEMBLY_LINE_RECIPES`、`CIRCUIT_ASSEMBLY_LINE_RECIPES`、`ANTIMATTER_FORGE_RECIPES`、`DYSON_SWARM_FUELS` 等。
完整名字：`javap -p -cp <dir> gregtechlite.gtlitecore.api.recipe.GTLiteRecipeMaps`。

`…api.recipe.builder` 下 14 个 builder：
`AccelerationTrackRecipeBuilder`、`AdvancedFusionRecipeBuilder`、`CircuitAssemblyLineRecipeBuilder`、
`ComponentAssemblyLineRecipeBuilder`、`MinimumHeightRecipeBuilder`、`MobProximityRecipeBuilder`、`NanoForgeRecipeBuilder`、
`NoCoilTemperatureRecipeBuilder`、`NoEnergyRecipeBuilder`、`PCBFactoryRecipeBuilder`、`PseudoMultiRecipeBuilder`、
`QuantumForceTransformerRecipeBuilder`（另有 `$Companion`）。

`MixinRecipeBuilder` 注入点：`private void requestEUtRange(CallbackInfoReturnable<ValidationResult<Recipe>>)` —— 即在 `build()` 返回处做 EUt 区间校验。

---

## 3. MCPotions（**JEI 硬依赖**）

| 全限定名 | jar 内路径 |
|---|---|
| `gregtechlite.gtlitecore.api.item.MCPotions` | `gregtechlite/gtlitecore/api/item/MCPotions.class` |

Kotlin `object`，`public static final MCPotions INSTANCE`。公开 API **只有一批静态 getter**，返回 `ItemStack`：

```java
static ItemStack getEMPTY();           getWATER();            getMUNDANE();          getTHICK();
static ItemStack getAWKWARD();         getNIGHT_VISION();     getLONG_NIGHT_VISION();getINVISIBILITY();
static ItemStack getLONG_INVISIBILITY(); getLEAPING();         getLONG_LEAPING();     getSTRONG_LEAPING();
static ItemStack getFIRE_RESISTANCE(); getLONG_FIRE_RESISTANCE(); getSWIFTNESS();    getLONG_SWIFTNESS();
static ItemStack getSTRONG_SWIFTNESS();getSLOWNESS();          getLONG_SLOWNESS();    getWATER_BREATHING();
static ItemStack getLONG_WATER_BREATHING(); getHEALING(); …（共 40 个 getter）
```

真正构造药水的方法 `private ItemStack potion(PotionType)` —— **私有，外部不可调用**，只能走 getter。

**JEI 硬依赖的证据（不是猜测）**：`MCPotions.class` 常量池里有
`mezz/jei/plugins/vanilla/brewing/BrewingRecipeUtil`；`<clinit>`（`MCPotions.kt:12`）调用
`potion(...)`（`MCPotions.kt:84`），后者 `invokestatic BrewingRecipeUtil.getBrewingRecipes()`。
因此**只要碰到 `MCPotions` 就要求 JEI/HEI 在运行期存在**，缺了会在注册配方阶段炸：

```
java.lang.NoClassDefFoundError: mezz/jei/plugins/vanilla/brewing/BrewingRecipeUtil
  at gregtechlite.gtlitecore.api.item.MCPotions.potion(MCPotions.kt:84)
  at gregtechlite.gtlitecore.api.item.MCPotions.<clinit>(MCPotions.kt:12)
  at gregtechlite.gtlitecore.loader.recipe.foodprocessing.MilkProcessing.init(MilkProcessing.kt:63)
```

本工程用 Had Enough Items 4.36.1（`modid=jei`）满足该依赖。

---

## 4. Meta 物品渲染系统

GTCEu 的 `MetaValueItem` 通过 `IItemComponent` 挂行为；GTLite Core 自定义了
`ItemRendererManager`（继承 `IItemComponent`）与一组 `*RenderBehavior` 接口，并用 Mixin 改写 `MetaItem` 的模型注册。

| 全限定名 | 作用 | jar 内路径 |
|---|---|---|
| `gregtechlite.gtlitecore.api.item.GTLiteMetaItem` | 自定义 `StandardMetaItem` 子类 | `…/api/item/GTLiteMetaItem.class` |
| `gregtechlite.gtlitecore.client.renderer.ItemRendererManager` | 渲染管理器组件基接口 | `…/client/renderer/ItemRendererManager.class` |
| `gregtechlite.gtlitecore.client.renderer.CustomItemRenderer` | 返回 `ItemRendererManager` | `…/client/renderer/CustomItemRenderer.class` |
| `…client.renderer.GlitchRenderBehavior` | 故障/闪烁渲染 | `…/client/renderer/GlitchRenderBehavior.class` |
| `…client.renderer.HaloRenderBehavior` | 光环渲染 | `…/client/renderer/HaloRenderBehavior.class` |
| `…client.renderer.UniversiumRenderBehavior` | 宇宙（着色器）渲染 | `…/client/renderer/UniversiumRenderBehavior.class` |
| `…client.renderer.MaskUniversiumRenderBehavior` | 掩码宇宙渲染 | `…/client/renderer/MaskUniversiumRenderBehavior.class` |
| `…client.renderer.TranscendentRenderBehavior` | 超越层渲染 | `…/client/renderer/TranscendentRenderBehavior.class` |
| `…mixins.gregtech.client.MixinMetaItem` | 改写模型路径注册 | `…/mixins/gregtech/client/MixinMetaItem.class` |
| `com.morphismmc.morphismlib.client.StaticRenderer`（morphismlib） | 静态物品模型渲染 | `com/morphismmc/morphismlib/client/StaticRenderer.class` |

关键签名：

```java
// GTLiteMetaItem extends gregtech.api.items.metaitem.StandardMetaItem
ResourceLocation createItemModelPath(MetaValueItem item, String path);
int   getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, IBlockState state);
float func_150893_a(ItemStack stack, IBlockState state);   // 挖速

// ItemRendererManager extends gregtech.api.items.metaitem.stats.IItemComponent
void onRendererRegistry(ResourceLocation location);

// CustomItemRenderer
ItemRendererManager getRendererManager();

// GlitchRenderBehavior extends ItemRendererManager
int    getGlitchPrimaryColor();
int    getGlitchSecondaryColor();
double getGlitchMaxOffset();
long   getGlitchFrameTimeNanos();
int    getGlitchLoopFrameCount();
int    getGlitchDurationFrameCount();
int    getGlitchMoveFrameCount();

// HaloRenderBehavior extends ItemRendererManager
TextureAtlasSprite getHaloTexture();
int     getHaloColor();
int     getHaloSize();
boolean shouldDrawHalo();
boolean shouldDrawPulse();

// morphismlib
static void StaticRenderer.renderItemModel(IBakedModel model, ItemStack stack, int color);
```

挂载方式（与 GTCEu 的 `IItemComponent` 机制一致）：给 `MetaValueItem` 调
`addComponents(...)` 传入你的 `ItemRendererManager` 实现；`MixinMetaItem` 在
`registerModels` 处回调 `createItemModelPath`，所以模型路径由 `GTLiteMetaItem` 子类决定。

---

## 5. Mixin 配置（jar 内 5 个 json，1 早期 + 4 晚期）

| 文件 | 加载器 | 内容 |
|---|---|---|
| `mixins.gtlitecore.minecraft.json` | `EarlyMixinLoader`（`IFMLLoadingPlugin` + `IEarlyMixinLoader`） | 3 个 client mixin |
| `mixins.gtlitecore.gregtech.json` | `LateMixinLoader` | 40 个 mixin（含 14 个 client） |
| `mixins.gtlitecore.appeng.json` | `LateMixinLoader` | 1 个 |
| `mixins.gtlitecore.jei.json` | `LateMixinLoader` | 1 个 |
| `mixins.gtlitecore.modularui.json` | `LateMixinLoader` | 1 个 |

共用字段：`package: gregtechlite.gtlitecore.mixins.<模块>`、`required: true`、
`refmap: mixins.gtlitecore.refmap.json`、`target: "@env(DEFAULT)"`、`minVersion: "0.8.3"`、
`compatibilityLevel: "JAVA_8"`。

`mixins.gtlitecore.minecraft.json`（早期，client 专用）：

```json
{
  "package": "gregtechlite.gtlitecore.mixins.minecraft",
  "required": true, "refmap": "mixins.gtlitecore.refmap.json",
  "target": "@env(DEFAULT)", "minVersion": "0.8.3", "compatibilityLevel": "JAVA_8",
  "mixins": [], "server": [],
  "client": ["client.MixinMinecraft", "client.MixinBlockGlass", "client.MixinPlayerControllerMP"]
}
```

`mixins.gtlitecore.appeng.json`：

```json
{
  "package": "gregtechlite.gtlitecore.mixins.appeng",
  "required": true, "refmap": "mixins.gtlitecore.refmap.json",
  "target": "@env(DEFAULT)", "minVersion": "0.8.3", "compatibilityLevel": "JAVA_8",
  "mixins": ["MixinContainerInterfaceTerminal"], "server": [], "client": []
}
```

`mixins.gtlitecore.jei.json`：

```json
{
  "package": "gregtechlite.gtlitecore.mixins.jei",
  "required": true, "refmap": "mixins.gtlitecore.refmap.json",
  "target": "@env(DEFAULT)", "minVersion": "0.8.3", "compatibilityLevel": "JAVA_8",
  "mixins": ["MixinItemStackRenderer"], "server": [], "client": []
}
```

`mixins.gtlitecore.modularui.json`：

```json
{
  "package": "gregtechlite.gtlitecore.mixins.modularui",
  "required": true, "refmap": "mixins.gtlitecore.refmap.json",
  "target": "@env(DEFAULT)", "minVersion": "0.8.3", "compatibilityLevel": "JAVA_8",
  "mixins": ["MixinPanelSyncManager"], "server": [], "client": []
}
```

`mixins.gtlitecore.gregtech.json`（最大的一份，40 个）：

```json
{
  "package": "gregtechlite.gtlitecore.mixins.gregtech",
  "required": true, "refmap": "mixins.gtlitecore.refmap.json",
  "target": "@env(DEFAULT)", "minVersion": "0.8.3", "compatibilityLevel": "JAVA_8",
  "mixins": [
    "AccessorMultiblockUIFactory", "AccessorMultiblockWithDisplayBase", "AccessorTraceabilityPredicate",
    "AccessorMetaTileEntityPowerSubstation", "MixinBlockPattern", "MixinMultiblockUIBuilder",
    "MixinMultiblockUIFactory", "MixinFuelRecipeLogic", "MixinMultiblockFuelRecipeLogic", "MixinDualHandler",
    "MixinItemHandlerList", "MixinMaterialProperties", "MixinBaseFilterContainer", "MixinMaterialRecipeHandler",
    "MixinMetaTileEntity", "MixinMetaTileEntityCleaningMaintenanceHatch", "MixinMetaTileEntityFluidHatch",
    "MixinMetaTileEntityHolder", "MixinMetaTileEntityItemBus", "MixinMetaTileEntityMEInputBus",
    "MixinMetaTileEntityMultiblockNotifiablePart", "MixinMetaTileEntityNetworkSwitch",
    "MixinMetaTileEntityRotorHolder", "MixinMetaTileEntityLargeBoiler", "MixinMetaTileEntityLargeChemicalReactor",
    "MixinMetaTileEntityLargeTurbine", "MixinMultiblockControllerBase", "MixinMultiblockRecipeLogic",
    "MixinBQuDataFixer", "MixinMetaTileEntityPowerSubstation", "MixinTextFormattingUtil",
    "MixinMultiMapMultiblockController", "MixinOreConfigUtils", "MixinOreRecipeHandler",
    "MixinPartsRecipeHandler", "MixinRecipeBuilder", "MixinWireCombiningHandler",
    "MixinWireRecipeHandler", "MixinJustEnoughItemsModule"
  ],
  "server": [],
  "client": [
    "client.MixinBlockGlassCasing", "client.MixinBlockGlassCasingType", "client.MixinCableRenderer",
    "client.MixinFluidPipeRenderer", "client.MixinItemPipeRenderer", "client.MixinMetaItem",
    "client.MixinMetaPrefixItem", "client.MixinMetaTileEntityFusionReactor", "client.MixinMetaTileEntityFluidHatch",
    "client.MixinMetaTileEntityItemBus", "client.MixinMetaTileEntityLargeTurbine",
    "client.MixinMetaTileEntityMultiblockPart", "client.MixinMetaValueItem", "client.MixinProspectingTexture"
  ]
}
```

沿用这套结构的写法（自己写 mixin 配置时）：`package` 指到你的 mixin 包；`required: true`；
`refmap` 用 RFG 生成的 `mixins.<modid>.refmap.json`；`compatibilityLevel: JAVA_8`；`minVersion 0.8.3`
（本工程 MixinBooter 10.7 满足）。GTLite Core 用 `IEarlyMixinLoader`（早期）+ `ILateMixinLoader`（晚期）两段加载，
模块化 json 按可选 mod（AE2 / JEI / ModularUI）拆分，缺 mod 时对应 json 里的 `@Mixin target` 只会告警不会崩。

## 未找到的条目

* `TemplateBlockRegistry` / `TemplateTierRegistry` 之类**以 Template 命名**的类：jar 内不存在（`grep -i template` 全空）。
  「Template Block Tier Registries」在这份构建里就是第 1 节那套 `BlockVariant` + `StateTier` + `BlockAttributeRegistry`。
* `api/render` 包：不存在；渲染入口只在 `client/renderer`。
