# Startup Flow

<p align="center">
  <img src="logo.png" alt="Startup Flow Logo" width="180">
  <br>
  <strong>High-performance startup optimizer for Minecraft 1.12.2</strong>
  <br>
  <em>CleanroomMC &bull; Minecraft Forge &bull; Client &bull; Dedicated Server</em>
</p>

<p align="center">
  <a href="https://github.com/Brilliafy/StartupFlow/releases"><img src="https://img.shields.io/badge/Minecraft-1.12.2-blue.svg" alt="Minecraft 1.12.2"></a>
  <a href="https://github.com/CleanroomMC/Cleanroom"><img src="https://img.shields.io/badge/Loader-Cleanroom%20%7C%20Forge-orange.svg" alt="Cleanroom & Forge"></a>
  <a href="https://github.com/Brilliafy/StartupFlow/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-green.svg" alt="GPL 3.0 License"></a>
  <img src="https://img.shields.io/badge/Java-8%20--%2025-brightgreen.svg" alt="Java 8 - 25">
</p>

---

## 🚀 Overview

**Startup Flow** is an intelligent startup acceleration mod designed for heavily modded **Minecraft 1.12.2** instances running on either **Cleanroom** or standard **Minecraft Forge**.

It eliminates two of the most notorious startup bottlenecks in 1.12.2 modpacks:
1. **FermiumBooter Jar Scan Caching (Coremod)**: Caches FermiumBooter's heavy classpath/JAR scan to disk, bypassing the repetitive scanning of hundreds of mod JARs on every boot.
2. **Parallel Model Baking & Atlas Mipmapping (Client Mixin)**: Distributes block and item model compilation across a multi-threaded worker pool and replaces texture atlas mipmapping with cache-coherent, thread-safe routines.

---

## ⚡ Key Benefits

- **Instant Replay of FermiumBooter Scans**: Replays hundreds of mod IDs and `@MixinConfig` toggles in **~24 ms** instead of repeating an exhaustive ClassGraph scan across the filesystem.
- **Multi-Core Model Baking**: Scales block and item model baking across all available CPU threads, dramatically speeding up client loading on large asset packs.
- **Smart Fingerprint Invalidation**: Automatically detects additions, updates, or removals of mods in `mods/` (including subdirectories like `mods/1.12.2`) and config updates via SHA-256 content hashing.
- **Universal Loader Compatibility**: Runs out of the box on both **Cleanroom** and standard **Forge 14.23.5.2847+**.
- **Server & Client Ready**: The jar scan cache speeds up both client launches and dedicated servers; client-only optimizations gracefully idle on dedicated servers.
- **Zero Hard Dependencies**: Uses safe reflection bridges for FermiumBooter; if FermiumBooter is not installed, it cleanly steps aside without error spam.
- **Native Mod Compatibility**: Built-in safeguards automatically handle OptiFine and respect VintageFix dynamic resource loading.

---

## 📊 Benchmark Results

*Tested on a heavy modpack with **234 mods** and **564 `@MixinConfig` toggles** (Cleanroom 0.6.13-alpha, identical machine and environment):*

| Run / Condition | State | Log Span (First &rarr; Done) | Wall Time (&plusmn;0.5s) | Toggle Processing |
|---|---|---|---|---|
| **Baseline (No StartupFlow)** | &mdash; | 56.0 s | 60.4 s | Full ClassGraph Scan |
| **StartupFlow (Cold Boot)** | Miss &rarr; Write | 55.0 s | 59.0 s | Full Scan + Cache Created |
| **StartupFlow (Warm Boot 1)** | **Cache Hit** | **53.0 s** | **57.6 s** | **Replayed in 24 ms** |
| **StartupFlow (Warm Boot 2)** | **Cache Hit** | **53.0 s** | **57.0 s** | **Replayed in 24 ms** |

> **Verification & Correctness**:
> - **0** Mixin errors, **0** crashes, and **0** exceptions across all test runs.
> - Cold boot vs. warm boot applied toggle sets: **612 lines compared, identical match**.
> - Degrades cleanly on vanilla Forge without FermiumBooter (logs informative INFO message, no errors).

---

## 🛠️ How It Works Under the Hood

### 1. Caching the FermiumBooter Jar Scan
In modpacks utilizing FermiumBooter, `FermiumJarScanner` uses ClassGraph to inspect every `.class` file inside every mod JAR on every boot to detect `@Mod`, `mcmod.info`, and `@MixinConfig` toggles. On packs with 200+ mods, this generates huge disk I/O and CPU overhead.

Startup Flow registers as an early non-tweaker coremod (`@SortingIndex(989)`, right before FermiumBooter's `990`). During coremod discovery:
1. It computes a SHA-256 fingerprint based on all mod file paths, file sizes, and modification timestamps across `mods/` and its subdirectories, combined with a SHA-256 content hash of `config/fermiumbooter.cfg`.
2. On a cache hit, it pre-populates `FermiumJarScanner.presentMods` and `mixinToggles` from `config/fermiumscancache.json` before any other coremod can trigger the scanner.
3. At `injectData`, it replays required classloader additions and applies all cached mixin configs, cutting scan time down to mere milliseconds.

### 2. Multi-Threaded Model Baking
Vanilla Forge compiles block and item models sequentially on a single thread. Startup Flow hooks `ModelLoader.setupModelRegistry()`:
- Spawns a dedicated worker pool (`ForkJoinPool`) matching your CPU core count.
- Concurrently bakes all models into a thread-safe `ConcurrentHashMap`.
- Safely manages Forge's `ProgressManager.ProgressBar` with step batching and guaranteed stack popping, ensuring the Forge splash screen never locks or crashes.

### 3. Accelerated Mipmap Generation
Vanilla mipmapping relies on a shared static buffer that prevents concurrency and performs redundant floating-point gamma conversions. Startup Flow:
- Uses a precomputed lookup table `COLOR_GAMMAS[256]` for fast gamma blending.
- Implements thread-safe stack-allocated buffers and cache-coherent row-major downsampling.
- Concurrently processes multi-frame animated textures across a dedicated worker pool.

---

## 🧩 Mod Compatibility

| Mod | Compatibility Status | Notes |
|---|---|---|
| **CleanroomMC** | ✅ Full Native Support | Fully tested and optimized for Cleanroom 0.6.x+. |
| **Minecraft Forge** | ✅ Full Native Support | Works on plain Forge 14.23.5.2847+ (requires MixinBooter). |
| **FermiumBooter** | ✅ Automatic Integration | Scan caching activates automatically when FermiumBooter is present. |
| **OptiFine** | ✅ Automatic Protection | Dynamically detected; StartupFlow disables its texture atlas mixin to leave OptiFine's custom shaders and texture scaling untouched. |
| **VintageFix** | ✅ Fully Compatible | If `dynamic_resources` is enabled in VintageFix, model baking is deferred to runtime; StartupFlow's model baker safely idles while scan caching continues working. |
| **Surge / CensoredASM** | ✅ Fully Compatible | No overlapping bytecode transformations. |

---

## 📋 Requirements

- **Minecraft**: `1.12.2`
- **Mod Loader**: Minecraft Forge (`14.23.5.2847` or newer) OR Cleanroom (`0.6.x` or newer)
- **Dependencies**: [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixinbooter) (`10.0` or newer)
- **Optional**: [FermiumBooter](https://www.curseforge.com/minecraft/mc-mods/fermiumbooter) (required only for scan caching)
- **Java**: Java 8 up to Java 25

---

## 📦 Installation

1. Make sure **MixinBooter** is installed in your `mods/` folder.
2. Drop `startupflow-<version>.jar` into your `mods/` directory.
3. Launch Minecraft! The first launch computes and stores the cache; subsequent launches will be significantly faster.

---

## ⚙️ Configuration

File: `config/startupflow.cfg`

```hocon
"Model Baking" {
    # Bake block and item models across multiple worker threads instead of a single thread.
    B:"Enable Parallel Model Baking"=true

    # Number of threads for model baking (0 = auto-detect CPU cores).
    I:"Model Baking Threads"=0

    # Compute texture atlas mipmap levels concurrently across multiple threads.
    B:"Enable Parallel Mipmaps"=true

    # Number of threads for mipmap generation (0 = auto-detect CPU cores).
    I:"Mipmap Threads"=0
}
```

### Cache Control
- **Disable the cache via JVM flag**: `-Dfermiumscancache.disable=true`
- **Disable via file marker**: Create an empty file at `config/fermiumscancache.disabled`
- **Force a rescan**: Delete `config/fermiumscancache.json`

---

## 💬 In-Game Commands

- `/startupflow status` &mdash; Displays thread configurations, effective worker counts, and parallel baking status.
- `/startupflow toggle` &mdash; Toggles parallel model baking on or off in real-time.

---

## 📜 Credits & License

- Developed by **Brilliafy**
- FermiumBooter by **fonnymunkey**
- Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for details.
