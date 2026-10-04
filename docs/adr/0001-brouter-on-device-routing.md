# Routes are generated on the phone by BRouter, not by a hosted routing API

Route generation runs offline on the phone through the separate BRouter Android app (called over its AIDL service), driven by our own running profile that exposes each route preference as a parameter. BRouter is the only free option that can weight all four route preferences (soft surface, scenic, low traffic, low incline), has no quota or API key, uses Lidar elevation above 60°N (good for Sweden), and stays publishable without a server.

## Considered Options

- **OpenRouteService**: real target-distance round trips and a generous free quota, but no soft-surface or incline weighting for foot, green/quiet coverage in Sweden unverified, and its terms forbid shipping an API key in the app (every user must bring their own).
- **GraphHopper Free**: best round-trip API, but no `custom_model` on the Free plan, so no route preference can be applied at all; non-commercial only.
- **Valhalla (FOSSGIS)**: no round-trip algorithm.
- **Embedding `brouter-core` in our app** instead of depending on the BRouter app: one app to install, but we'd own segment downloads and engine updates. Deferred until publishing is on the table.

## Consequences

- The runner installs BRouter and downloads the Sweden segments once.
- BRouter's round trip takes a radius, not a length, so our app calibrates (generate, measure, rescale) until a route is within −0%/+8% of the target distance (aiming for +3%); point-to-point routes are padded into the same band with our own detour points. When rescaling oscillates, the app bisects the radius, then nudges the direction, then replaces the direction with a fresh one; only then does it fall back to the closest result, flagged (originally ±5%, amended after the smoke test showed loop length jumping across the band).
