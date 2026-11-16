Copied into the bake's config folder (`.cache/bake-run/config`) before every bake: configuration that decides how
mods load and which of their mixins are applied at build time.

* `fabric_loader_dependencies.json`: dependency overrides read by Fabric Loader. Sodium Extra 0.4.0 declares it
  breaks Sodium `0.2.0+IRIS_rev.400af47`; current Fabric Loader ignores build metadata when comparing versions, so
  that also matched the official Sodium `0.2.0+build.4` it was made for. The rest are Fabulously Optimized's own
  overrides (optional update checkers).
