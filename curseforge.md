<div align="center">
  <h1>⚡ Startup Flow</h1>
  <p><strong>High-Performance Startup Optimizer for Minecraft 1.12.2</strong></p>
  <p><em>CleanroomMC &bull; Minecraft Forge &bull; Client &bull; Dedicated Server</em></p>
  <p>
    <img src="https://img.shields.io/badge/Minecraft-1.12.2-blue.svg" alt="Minecraft 1.12.2" />
    <img src="https://img.shields.io/badge/Loader-Cleanroom%20%7C%20Forge-orange.svg" alt="Cleanroom and Forge" />
    <img src="https://img.shields.io/badge/Environment-Client%20%26%20Server-green.svg" alt="Client and Server" />
    <img src="https://img.shields.io/badge/License-GPL--3.0-brightgreen.svg" alt="GPL 3.0" />
  </p>
</div>

<hr />

<h2>🌟 What is Startup Flow?</h2>
<p>
  Tired of waiting minutes every time you boot up large Minecraft 1.12.2 modpacks like <em>RLCraft</em>, <em>MeatballCraft</em>, or <em>GreedyCraft</em>?
</p>
<p>
  <strong>Startup Flow</strong> is a dual-purpose optimization mod designed specifically to eliminate startup lag on both <strong>CleanroomMC</strong> and standard <strong>Minecraft Forge 1.12.2</strong>. It tackles two of the heaviest launch bottlenecks:
</p>
<ol>
  <li><strong>Caching FermiumBooter's Startup Scan</strong>: Bypasses the repetitive, disk-heavy ClassGraph scanning of hundreds of mod JAR files on every boot by caching the results to disk.</li>
  <li><strong>Multi-Threaded Model Baking</strong>: Parallelizes block and item model compilation across multiple worker threads instead of forcing everything through a single CPU core.</li>
</ol>

<hr />

<h2>⚡ Key Benefits &amp; Features</h2>
<ul>
  <li>
    <strong>Instant Replay of Jar Scans:</strong> On warm boots, Startup Flow pre-loads cached mod IDs and <code>@MixinConfig</code> toggles into FermiumBooter in just <strong>~24 milliseconds</strong>, completely skipping the expensive disk scan.
  </li>
  <li>
    <strong>Multi-Core Model Baking:</strong> Automatically detects your CPU cores and bakes block/item models concurrently, dramatically speeding up client loading on packs with tens of thousands of model variants.
  </li>
  <li>
    <strong>Smart Invalidation Fingerprint:</strong> Automatically detects changes in your <code>mods/</code> directory (including nested folders like <code>mods/1.12.2/</code>) as well as <code>config/fermiumbooter.cfg</code> content changes. The cache updates itself only when your mod setup actually changes.
  </li>
  <li>
    <strong>Universal Compatibility:</strong> Works natively on both <strong>Cleanroom</strong> and standard <strong>Forge</strong> instances.
  </li>
  <li>
    <strong>Client &amp; Server Friendly:</strong> Caching benefits both client launches and dedicated servers. Model baking optimizations safely stand by on headless servers.
  </li>
  <li>
    <strong>Safe &amp; Non-Intrusive:</strong> Uses reflection with zero compile-time dependencies on FermiumBooter. If FermiumBooter is absent, Startup Flow steps aside gracefully without logging scary errors.
  </li>
  <li>
    <strong>Built-in OptiFine &amp; VintageFix Protection:</strong> Automatically detects OptiFine and disables conflicting texture mixins to preserve custom shaders and textures. Fully compatible with VintageFix's dynamic resources.
  </li>
</ul>

<hr />

<h2>📊 Real Benchmark Results</h2>
<p>
  <em>Measured on a real-world modpack with <strong>234 mods</strong> and <strong>564 <code>@MixinConfig</code> toggles</strong> (Cleanroom 0.6.13-alpha, identical hardware):</em>
</p>

<table border="1" cellpadding="8" cellspacing="0" width="100%">
  <thead>
    <tr bgcolor="#2d3748" style="color: #ffffff;">
      <th align="left">Boot State</th>
      <th align="center">Cache Status</th>
      <th align="center">Log Span (Start &rarr; Done)</th>
      <th align="center">Wall Time (&plusmn;0.5s)</th>
      <th align="left">Toggle Scan Processing</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Baseline (No StartupFlow)</strong></td>
      <td align="center"><em>None</em></td>
      <td align="center">56.0 s</td>
      <td align="center">60.4 s</td>
      <td>Full ClassGraph JAR scan across disk</td>
    </tr>
    <tr>
      <td><strong>StartupFlow (Cold Boot)</strong></td>
      <td align="center">Miss &rarr; Write</td>
      <td align="center">55.0 s</td>
      <td align="center">59.0 s</td>
      <td>Scans once and caches to disk</td>
    </tr>
    <tr bgcolor="#f0fff4">
      <td><strong>StartupFlow (Warm Boot 1)</strong></td>
      <td align="center"><strong>Cache Hit</strong></td>
      <td align="center"><strong>53.0 s</strong></td>
      <td align="center"><strong>57.6 s</strong></td>
      <td><strong>564 toggles applied in 24 ms</strong></td>
    </tr>
    <tr bgcolor="#f0fff4">
      <td><strong>StartupFlow (Warm Boot 2)</strong></td>
      <td align="center"><strong>Cache Hit</strong></td>
      <td align="center"><strong>53.0 s</strong></td>
      <td align="center"><strong>57.0 s</strong></td>
      <td><strong>564 toggles applied in 24 ms</strong></td>
    </tr>
  </tbody>
</table>

<blockquote>
  <p><strong>Correctness Guarantee:</strong> 0 Mixin errors, 0 crashes, and identical toggle evaluation verified between cold and warm boots.</p>
</blockquote>

<hr />

<h2>🧩 Compatibility Matrix</h2>
<ul>
  <li><strong>CleanroomMC:</strong> Fully supported and recommended.</li>
  <li><strong>Minecraft Forge 1.12.2:</strong> Fully supported on Forge <code>14.23.5.2847</code> or higher.</li>
  <li><strong>FermiumBooter:</strong> Supported automatically. The scan cache activates when FermiumBooter is installed.</li>
  <li><strong>OptiFine:</strong> Fully compatible. Startup Flow detects OptiFine at runtime and dynamically disables its texture atlas mixin so OptiFine's shader and texture pipelines remain 100% untouched.</li>
  <li><strong>VintageFix:</strong> Fully compatible. If VintageFix has <code>dynamic_resources</code> enabled (loading models on-demand), Startup Flow's model baker safely idles while the FermiumBooter scan cache continues speeding up startup.</li>
  <li><strong>Surge / CensoredASM:</strong> Fully compatible with no conflicting transformations.</li>
</ul>

<hr />

<h2>📋 Requirements &amp; Installation</h2>
<h3>Requirements:</h3>
<ul>
  <li>Minecraft <code>1.12.2</code></li>
  <li>Minecraft Forge (<code>14.23.5.2847+</code>) OR Cleanroom (<code>0.6.x+</code>)</li>
  <li><a href="https://www.curseforge.com/minecraft/mc-mods/mixinbooter"><strong>MixinBooter</strong></a> (<code>10.0</code> or newer)</li>
  <li><em>Optional:</em> <a href="https://www.curseforge.com/minecraft/mc-mods/fermiumbooter">FermiumBooter</a> (required only if you want scan caching)</li>
  <li>Java 8 up to Java 25</li>
</ul>

<h3>Installation:</h3>
<ol>
  <li>Ensure <strong>MixinBooter</strong> is present in your <code>mods</code> folder.</li>
  <li>Download <code>startupflow-1.0.1.jar</code> and place it into your <code>mods</code> folder.</li>
  <li>Launch the game. The first boot records the cache; every launch thereafter will automatically benefit from warm boots!</li>
</ol>

<hr />

<h2>⚙️ Configuration &amp; Commands</h2>
<p>Configuration file: <code>config/startupflow.cfg</code></p>
<pre><code>"Model Baking" {
    # Bake block and item models across multiple worker threads instead of a single thread.
    B:"Enable Parallel Model Baking"=true

    # Number of threads for model baking (0 = auto-detect CPU cores).
    I:"Model Baking Threads"=0

    # Compute texture atlas mipmap levels concurrently across multiple threads.
    B:"Enable Parallel Mipmaps"=true

    # Number of threads for mipmap generation (0 = auto-detect CPU cores).
    I:"Mipmap Threads"=0
}</code></pre>

<h3>Cache Management:</h3>
<ul>
  <li><strong>Disable Cache via JVM flag:</strong> Add <code>-Dfermiumscancache.disable=true</code> to your launcher arguments.</li>
  <li><strong>Disable Cache via File Marker:</strong> Place an empty file at <code>config/fermiumscancache.disabled</code>.</li>
  <li><strong>Force Rescan:</strong> Delete <code>config/fermiumscancache.json</code>.</li>
</ul>

<h3>In-Game Commands:</h3>
<ul>
  <li><code>/startupflow status</code> &mdash; View current model baking threads and status.</li>
  <li><code>/startupflow toggle</code> &mdash; Toggle parallel model baking on or off.</li>
</ul>

<hr />

<h2>📜 License</h2>
<p>
  Startup Flow is open-source software licensed under the <strong>GNU General Public License v3.0 (GPL-3.0)</strong>. Source code is available on <a href="https://github.com/Brilliafy/StartupFlow">GitHub</a>.
</p>
