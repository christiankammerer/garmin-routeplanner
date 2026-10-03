# Free routing engines for loop routes with route preferences

Research for [#4](https://github.com/christiankammerer/garmin-routeplanner/issues/4) (map: [#1](https://github.com/christiankammerer/garmin-routeplanner/issues/1)).
Researched 2026-10-03 against primary sources (official API specs, source code, pricing/terms pages).

**Question:** which free routing options, usable from a Kotlin Android app with no own server and no licence, can produce a
**loop route** (and a **point-to-point route**) near a **target distance** (±5%), yield ~3 **candidate routes** per
**route request**, and honour **route preferences** (soft surface, scenic, low traffic, low incline)?

Legend: **[V]** verified in the cited primary source. **[I]** inference from the sources (not tested against a live API).
**[U]** unverified / could not be confirmed.

## Summary

| | GraphHopper hosted API (Free) | OpenRouteService (HeiGIT, Standard) | BRouter (on-device Android app) | Valhalla (FOSSGIS public server) |
|---|---|---|---|---|
| Loop route | Yes, `algorithm=round_trip` + `round_trip.distance` [V] | Yes, `options.round_trip.length` [V] | Yes, `engineMode=4` (since 1.7.8) [V], but parameter is a radius, not a length [V] | No native loop; app must synthesise via-points [V/I] |
| Point-to-point | Yes [V] | Yes [V] | Yes [V] | Yes [V] |
| Multiple candidates | Vary `round_trip.seed` (1 call per candidate) [V] | Vary `round_trip.seed` [V]; `alternative_routes` not usable for loops [V] | Vary `direction` (bearing, or -1 = random) [V]; `alternativeidx` 0–3 [V] | `alternates` (point-to-point only) [V]; loops: vary synthesised via-points [I] |
| Soft surface | **No on Free**: `custom_model` needs flexible mode, which Free excludes [V]. Only output (`details=surface`) [V] | No weighting; output only (`extra_info=surface`) [V]. `custom_model` not on public API [V] | Yes, fully scriptable profile (`remoteProfile`, `extraParams`) [V] | Partial: `use_tracks`, `walkway_factor` (pedestrian) [V] |
| Scenic / green | No on Free [V] | `green` weighting for `foot-*` [V]; coverage in Sweden **[U]** | Yes, `forest_class`, `river_class` pseudo-tags in profiles [V] | No [V] |
| Low traffic | No on Free [V] | `quiet` weighting for `foot-*` [V]; coverage in Sweden **[U]** | Yes, `traffic_class`, `noise_class`, `town_class` pseudo-tags [V] | Weak proxy only (walkway/living-street factors) [V] |
| Low incline | No on Free [V] (incline needs `custom_model`) | No for foot (`steepness_difficulty` is `cycling-*` only) [V] | Yes, profile up/downhill costs; elevation-aware [V] | Yes, `use_hills` 0–1 for pedestrian [V] |
| Elevation data | `elevation=true` [V] | `elevation=true` (SRTM) [V/I] | SRTM, **Lidar above 60°N** [V] | `elevation_interval` [V] |
| Free quota | 500 credits/day; round trip = 2 credits [V] | Directions 2,000/day, 40/min [V] | Unlimited (on-device) [V] | ~1 req/s per user, fair use [V] |
| Terms: personal | OK [V] | OK, "free to use for everyone" [V] | OK, MIT, no account [V] | OK, fair use [V] |
| Terms: publishable | Free plan is **non-commercial**; key embedded in app is shared by all users [V/I] | Key must **not** ship in the app; each user must bring their own key [V] | Yes (users install BRouter + download segments) [V/I] | Allowed, but tell maintainers + send `X-Client-Id` [V] |
| Kotlin effort | Low: HTTPS + JSON [I] | Low: HTTPS + JSON [I] | Medium: AIDL bind to separate app, or embed MIT core library; write own running profile [I] | Medium-high: own loop generator on top of HTTPS [I] |

**Short recommendation (not the decision; that belongs to its own grilling ticket):** BRouter is the only option that
covers **all four** route preferences, has no quota, and is publishable without a server. Its cost is integration
effort (separate app plus offline segment download, a custom running profile, and a client-side distance-calibration loop,
because its round-trip parameter is a radius). OpenRouteService is the strongest hosted option (round trip, green/quiet,
2,000 calls/day), but green/quiet coverage outside Germany is unconfirmed, it cannot weight soft surface or incline for
walking, and publishing requires each user to bring their own key. The GraphHopper Free plan has the best round-trip
API, but without `custom_model` it cannot apply *any* route preference, and it is non-commercial only. Valhalla has no loop
algorithm. A fallback worth considering: BRouter for preferences plus loop generation, with one hosted API kept as a backup.

## GraphHopper hosted Directions API (Free plan)

- **Loop routes** [V]: `algorithm=round_trip`, `round_trip.distance` ("approximative length of the resulting round trip",
  default 10000), `round_trip.seed` ("Change this to get a different tour for each value"). Source: GraphHopper OpenAPI
  spec, `https://docs.graphhopper.com/_spec/openapi.yaml` (Routing → `algorithm`, `round_trip.*`).
- **Heading** for the loop's first leg "Requires `ch.disable=true`" [V] (same spec, `heading`), so it is not available on Free.
- **Point-to-point and alternatives** [V]: `algorithm=alternative_route` with `alternative_route.max_paths` (default 2),
  `max_weight_factor`, `max_share_factor` (same spec).
- **Route preferences** [V]: only through `custom_model` (priority/speed rules on encoded values such as `surface`,
  `track_type`, `road_class`, `urban_density`, `average_slope`, `max_slope`; spec, Custom Model section). The spec says
  `"ch.disable": true` "is required to make use of `custom_model`". The pricing page says the Free plan "cannot use the
  flexible mode (ch.disable=true)" ([pricing](https://www.graphhopper.com/pricing/)). **So on Free, none of the four
  route preferences can be expressed.** Path details (`details=surface`, `road_class`, …) can still be read back, for
  post-hoc scoring of candidate routes [V/I].
- **Profiles** [V]: "the free package supports only the routing profiles `car`, `bike` or `foot`" (spec, profiles note).
- **Elevation** [V]: `elevation=true` adds altitude to every point (spec). Whether the `foot` profile on Free returns
  elevation is [U].
- **Quota** [V]: Free = 500 credits/day, limited credits per minute, max 5 locations per request ([pricing](https://www.graphhopper.com/pricing/)).
  Round trip "costs 2 simple credits"; alternative routes cost 1 credit more ([credit FAQ](https://support.graphhopper.com/support/solutions/articles/44000718211-what-is-one-credit-)).
  Cheapest paid plan is €69/month (excluded by the constraints).
- **Terms** [V]: "The Free Plan is for non-commercial use only" (pricing). ToS §5.1: commercial use of Free is "allowed in
  the development phase and for the production phase on inquiry"; §5.2 results "may be temporarily cached on the client
  side (e.g. browser or mobile app)"; §3 GraphHopper + OSM attribution required ([terms](https://www.graphhopper.com/terms/)).
  [I] Permanently keeping a **saved route** may stretch "temporarily cached"; worth a check if this option is chosen.
  [I] A published app would ship one key shared by every user's 500 credits/day.
- **Target-distance accuracy** [V/I]: in the open-source engine the hosted API is built on, for loops under 50 km
  `RoundTripRouting` places a single generated point at about `distance / 3` beeline from the start, with slight random
  variation, then routes start → point → start
  (`core/src/main/java/com/graphhopper/routing/RoundTripRouting.java`, `MultiPointTour.java` in
  [graphhopper/graphhopper](https://github.com/graphhopper/graphhopper)). The road distance therefore depends on how
  winding the network is. [I] Expect results to often fall outside ±5%: retry with new seeds, or scale the requested distance
  by the observed ratio. At 2 credits per try, 3 candidates × ~3 tries ≈ 18 credits per route request ≈ 25 route requests/day.

## OpenRouteService (HeiGIT public API, Standard plan)

- **Loop routes** [V]: `options.round_trip` with `length` ("preferred value, but results may be different"), `points`
  ("Larger values create more circular routes"), `seed` ([routing options](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/routing-options),
  source `docs/api-reference/endpoints/directions/routing-options.md` in [GIScience/openrouteservice](https://github.com/GIScience/openrouteservice)).
  A round trip takes a single coordinate (`RoutingService.java`, `isRoundTrip`).
- **Alternatives** [V]: `alternative_routes` rejects requests with more than 2 waypoints (`RoutingService.java`:
  `IncompatibleParameterException … (number of waypoints > 2)`). So it works for point-to-point routes. [I] For loops,
  candidates come from varying `seed`/`points`.
- **Route preferences** [V] (routing-options.md):
  - `profile_params.weightings.green` ("prefer ways through green areas") and `quiet` ("prefer quiet ways") for
    **`foot-*`** profiles, with a factor 0–1. These map to *scenic* and *low traffic*.
  - `steepness_difficulty` is **`cycling-*` only**, so there is no incline preference for walking/running.
  - `avoid_features` for foot: `ferries`, `fords`, `steps` only.
  - `custom_model` is "currently not available on our public API for any profile" ([custom models](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/custom-models)), so there is **no soft-surface weighting**.
  - Output-only `extra_info`: `surface`, `steepness`, `waytype`, `green`, `noise` on `foot-walking`/`foot-hiking`; `shadow` and
    `csv` have "No data available in the public openrouteservice hosted by HeiGIT" ([extra info](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/extra-info/)).
  - **[U] Coverage of green/quiet data in Sweden.** The docs give no geographic limit. Older secondary write-ups describe
    these features as Germany-only. A 2025 maintainer comment says "We will be introducing a new green indicator soon"
    ([GIScience/openrouteservice#650](https://github.com/GIScience/openrouteservice/issues/650)). Needs one live test
    with a key in Sweden before relying on it.
- **Elevation** [V/I]: `elevation=true` on directions. The ToS credits SRTM (srtm.csi.cgiar.org) as the elevation source.
  [I] CGIAR SRTM ends at 60°N, so elevation north of roughly Gävle may be missing or coarse.
- **Quota** [V]: Standard plan "Free to use for everyone"; directions (`DirectionsForPublic_v2`) 2,000/day and 40/min
  ([plans page](https://account.heigit.org/info/plans), JS bundle data; [FAQ](https://giscience.github.io/openrouteservice/frequently-asked-questions):
  "default limit of 2000 requests per day … 40 directions requests" per minute). Self-hosted limits also cap round-trip and
  alternative-route distance at 100 km by default (`service.md`), which is irrelevant for running.
- **Terms** [V]: "every HeiGIT API key belongs to one person. Thus, an API key must not be used client-side in an
  application." The two sanctioned patterns are a server-side proxy (excluded) or "Any user of the application enters
  their own API key" ([FAQ](https://giscience.github.io/openrouteservice/frequently-asked-questions)). The ToS requires
  "© openrouteservice by HeiGIT | Data from OpenStreetMap" attribution and says results are licensed CC BY-SA 4.0
  ([ToS](https://account.heigit.org/info/tos)). [I] For personal use the owner's own key in his own app fits pattern 2;
  publishing means every user signs up for a key.
- **Target-distance accuracy** [I]: ORS is built on GraphHopper and its round trip has the same `length/points/seed`
  shape, so expect similar behaviour: approximate length, with retries or scaling needed for ±5%. With 2,000 calls/day,
  retries are cheap.

## BRouter (offline, on-device Android app)

- **What it is** [V]: an offline routing engine, available as an Android app from F-Droid, Google Play or APK
  ([android quickstart](https://github.com/abrensch/brouter/blob/master/docs/users/android_quickstart.md)). It exposes an
  Android bound service (`IBRouterService.aidl`, method `getTrackFromParams(Bundle)`) that other apps call
  ([android_service.md](https://github.com/abrensch/brouter/blob/master/docs/developers/android_service.md)). Licence MIT
  (GitHub licence API). Routing data comes as offline segment files the user downloads [V].
- **Loop routes** [V]: `engineMode=4` ("round trip"), added in release 1.7.8 (12.07.2025); current release is 1.7.10
  (17.07.2026) ([revisions](https://github.com/abrensch/brouter/blob/master/docs/revisions.md)). Parameters:
  `roundTripDistance` is the "radius to the round trip points in meters (default 1500)", `roundTripPoints` (default 5),
  and `direction` (initial bearing; "-1 = random").
  Implementation (`RoutingEngine.doRoundTrip`/`buildPointsFromCircle`): it places `points - 1` via-points on a circle of
  that radius around the start, fanning out from the bearing, and routes through them back to the start. If the profile
  sets `consider_forest`/`consider_river`/`consider_elevation`, the random bearing is biased toward forest, river or
  elevation areas (`getRandomDirectionFromData`).
- **Multiple candidates** [V]: vary `direction` (e.g. 0°/120°/240°), or use `alternativeidx` 0–3 (AIDL), which BRouter
  describes as useful "if … you are planning a roundtrip and don't want to go back the same way"
  ([alternatives](https://github.com/abrensch/brouter/blob/master/docs/features/alternatives.md)).
- **Route preferences** [V]: profiles are scripts (`.brf`) evaluated per way, and can be sent at call time as
  `remoteProfile` or tuned through `extraParams`
  ([profile developers guide](https://github.com/abrensch/brouter/blob/master/docs/developers/profile_developers_guide.md)).
  They can use OSM tags (surface, highway, tracktype …) plus precomputed pseudo-tags `noise_class`, `river_class`,
  `forest_class`, `town_class`, `traffic_class`
  ([environmental considerations](https://github.com/abrensch/brouter/blob/master/docs/developers/environmental_considerations_and_pseudo_tags.md)).
  All four route preferences can therefore be expressed as weights. [I] There is no stock "running" profile (the shipped
  profiles include `hiking-mountain`, `trekking`, `shortest`, …), so the app would carry its own profile with
  preference strengths injected as parameters.
- **Elevation** [V]: SRTM, and "For latitudes above 60 degree in northern Europe, BRouter uses Lidar data"; it computes a
  noise-filtered ascent ([elevation](https://github.com/abrensch/brouter/blob/master/docs/features/elevation.md)).
  This is a real plus for Sweden.
- **Quota / cost / terms** [V/I]: none; it runs on the phone. OSM data → ODbL attribution [I].
- **Target-distance accuracy** [I]: the API takes a radius, not a length. Loop length ≈ radius × (shape factor) ×
  (network detour). The app must calibrate, for example start with r ≈ target / 5–6, measure, and rescale r with a
  secant or bisection step until within ±5%. On-device calls cost nothing but take time and battery; the AIDL notes say
  "call in a background thread, heavy task!".
- **Integration** [I]: bind to the BRouter service with AIDL from Kotlin (copy the `.aidl`), parse GPX/GeoJSON output.
  The runner must install BRouter and download Sweden's segments once. An alternative is embedding the MIT-licensed
  `brouter-core` in the app, which removes the separate-app dependency at a larger maintenance cost.

## Valhalla (FOSSGIS public demo server)

- **Server** [V]: FOSSGIS hosts a public full-planet server; the API is at `valhalla1.openstreetmap.de`. Use "follows the
  usual fair-usage policy as OSRM & Nominatim demo servers (somewhat enforced by rate limits)". For apps published to end
  users, maintainers ask to be told via GitHub Discussions and to receive an `X-Client-Id` header
  ([README](https://github.com/valhalla/valhalla#demo-server)). The rate limit is 1 call/user/s and 100 calls/s total
  ([discussion #3373](https://github.com/valhalla/valhalla/discussions/3373)).
- **Loop routes** [V]: the route API ([api-reference](https://valhalla.github.io/valhalla/api/route/api-reference/))
  has no round-trip algorithm. [I] Loops need client-side via-point generation (start → generated points → start),
  similar to BRouter's circle approach, written by us.
- **Alternatives** [V]: `alternates`, "not yet supported on multipoint routes (that is, routes with more than 2 locations)".
  So it works for point-to-point routes only.
- **Route preferences** [V] (pedestrian costing): `use_hills` (0 = avoid hills), `use_tracks`, `walkway_factor`,
  `use_living_streets`, `use_lit`, `max_hiking_difficulty`. There is no green or traffic weighting. Surface is only indirect
  (tracks vs walkways).
- **Elevation** [V]: `elevation_interval` returns elevation along the route.
- **Fit** [I]: the weakest fit for loops. Its main value would be as a point-to-point fallback with an incline preference.

## Other options checked

- **Geoapify** [V]: free 3,000 credits/day, commercial use allowed ([pricing](https://www.geoapify.com/pricing/)); `walk`/`hike`
  modes, `details=elevation`/`route_details` (surface). There is no round trip and no green/quiet/surface weighting in the
  [routing docs](https://apidocs.geoapify.com/docs/routing/). Point-to-point only.
- **Stadia Maps** [V]: the free plan excludes routing ("Basic APIs only") and bans commercial use ([pricing](https://stadiamaps.com/pricing/)).
- Not researched in depth: the OSRM demo server (no round trip, no preferences), and OsmAnd (no service API for third-party
  route generation known).

## Open questions worth a quick live test

1. ORS: do `green`/`quiet` change routes in Sweden, i.e. is there data?
2. GraphHopper Free: does `round_trip` work without `ch.disable=true`? The spec does not say it needs it, unlike `heading`.
   Does `foot` return elevation?
3. All engines: the typical distance error of a single loop call in a Swedish suburb or forest, which decides how many
   retries the ±5% target costs.
