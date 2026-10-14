# Startup Flow

![StartupFlow](logo.png)

Startup optimization for Minecraft 1.12.2 on Cleanroom or Minecraft Forge.

Startup Flow is both a coremod and a client mod. The coremod part removes a slow startup scan when
FermiumBooter is installed. The client part makes model baking and mipmap generation use multiple
threads.

## Caching the FermiumBooter jar scan

FermiumBooter's `FermiumJarScanner` uses ClassGraph to read every class file in every mod jar at
launch. It does this to collect mod ids and `@MixinConfig` toggle classes. On a large pack that scan
can take tens of seconds, and it repeats on every launch because the result is not cached.

Startup Flow caches the scan result on disk and feeds it back to the scanner before the scan starts.
The saved file is `config/fermiumscancache.json` and it is keyed on a fingerprint of the `mods` folder
plus `config/fermiumbooter.cfg`, so adding, removing or updating a mod invalidates it automatically.

The timing here is the important part. The scan starts on the first call to
`FermiumRegistryAPI.isModPresent`, and at least one coremod calls that from its constructor during
coremod discovery. Startup Flow's coremod is a non-tweaker coremod, so it is constructed earlier in
that discovery pass than those plugins, which lets it load the cache before the scan can start. The
cached toggle classes are then queued from `injectData`, just before FermiumBooter's own.

Startup Flow does not modify FermiumBooter. It uses reflection only, with no compile time dependency,
and it falls back to a normal scan on any error. A normal FermiumBooter update keeps working.

### If FermiumBooter is not installed

There is nothing to cache when FermiumBooter is absent, so the coremod logs that and does nothing.
The model baking half of the mod still works normally. Startup Flow is safe to leave installed in a
pack that does not use FermiumBooter.

### Controlling the cache

- Disable it with the JVM flag `-Dfermiumscancache.disable=true`.
- Or create an empty file at `config/fermiumscancache.disabled`.
- Delete `config/fermiumscancache.json` to force a fresh scan on the next launch.

## Parallel model baking

Vanilla bakes block and item models on a single thread. Startup Flow bakes them on a small worker pool
and writes the results into a concurrent map. The bake path also produces mipmaps for the texture
atlas on a single thread, so those are generated in parallel too. Mipmap generation is written to
produce the same pixels as vanilla.

## Requirements

- Minecraft 1.12.2
- Minecraft Forge 14.23.5.x or Cleanroom 0.6.x
- MixinBooter 10 or newer
- FermiumBooter is optional. The cache applies only when it is present.
- Java 8

## Installation

Put `startupflow-<version>.jar` into your `mods` folder. The cache half is useful on both the client
and a dedicated server. The model baking half is client only.

## Configuration

File: `config/startupflow.cfg`

### Model Baking

| Option | Default | Description |
|---|---|---|
| Enable Parallel Model Baking | true | Bake block and item models on a worker pool. |
| Model Baking Threads | 0 | Worker threads. 0 uses the available CPU count. |
| Enable Parallel Mipmaps | true | Generate atlas mipmaps on multiple threads. |
| Mipmap Threads | 0 | Mipmap threads. 0 uses the available CPU count. |

The cache itself has no config entry. Use the flag or marker file described above.

## Commands

- `/startupflow status` shows the model baking thread settings.
- `/startupflow toggle` enables or disables parallel model baking.

## Notes and limitations

- The first launch after installing the mod, or after any change to your mods folder, still runs the
  full scan. Every launch after that skips it.
- The cache stores mod ids and mixin toggle data only. It does not cache chunk or texture data.

## Credits

Written by Brilliafy. FermiumBooter is by fonnymunkey.

## License

GPL-3.0. Copyright (C) 2026 Brilliafy. See the LICENSE file.
