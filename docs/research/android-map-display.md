# What map can the Android app show for free?

Research for [#6](https://github.com/christiankammerer/garmin-routeplanner/issues/6). Researched 2026-10-03.

**Question:** which map-display options can the Android app (Kotlin + Jetpack Compose) use to show the ~3 **candidate routes** for a **route request** on a map in Sweden, at zero cost and with no own server, and what do their usage policies allow for personal use versus a later published app?

Each claim below is marked **[verified]** (read in the cited primary source) or **[inference]** (my reading or judgement, not stated by the source).

## Answer

Use **MapLibre Native for Android**, driven from Compose through **MapLibre Compose**, with **OpenFreeMap** vector tiles (the `liberty` style). Draw each candidate route as a GeoJSON source plus a `LineLayer`. This costs nothing and needs no API key, no account and no own server. OpenFreeMap allows commercial use, so the same setup works if the app is published later.

Its weak spot is terrain. OpenFreeMap has no contours or hillshade at running zoom levels, only footpaths and land use. If terrain detail turns out to matter, a renderer-agnostic fallback is to add a free-tier key from Thunderforest (Outdoors) or MapTiler (Outdoor) as an alternative style. Those free tiers are for personal or hobby use, so they would have to be revisited before publishing.

Avoid `tile.openstreetmap.org` (no offline use, no SLA, an app must identify itself and can be blocked), osmdroid (archived) and the Google Maps SDK (the map itself is free, but it needs a billing account and API key, and it forbids offline caching).

## Comparison

| Option | Cost | Key / account | Personal vs published | Compose | Offline | Route lines | Terrain / outdoor detail |
|---|---|---|---|---|---|---|---|
| **MapLibre Native + OpenFreeMap** | Free | None | Commercial use allowed | MapLibre Compose (beta) | Offline packs in the SDK; OpenFreeMap ToS unclear on bulk download | GeoJSON + `LineLayer` | Paths yes; no contours or hillshade |
| MapLibre Native + Protomaps hosted API | Free for non-commercial use | API key | Commercial use needs GitHub sponsorship | same | On-device `.pmtiles` file works (no pack caching) | same | Basemap only |
| MapLibre Native + MapTiler free | Free up to quota | Account + key, no card | Personal or non-commercial only | same | Not checked | same | Outdoor/terrain styles exist, limited on free tier |
| MapLibre Native + Stadia free | Free up to 200k credits/month | Account + key, no card | Non-commercial or evaluation only | same | On-device cache up to 100 MB | same | Not verified on free tier |
| Thunderforest Hobby (raster) | Free up to 150k tiles/month | Account + key | Free plan can be withdrawn at their discretion | via MapLibre raster source | On-device caching allowed | same | Outdoors / Landscape styles |
| `tile.openstreetmap.org` | Free (donation-funded) | None, but a unique User-Agent is required | Can be blocked without notice | via MapLibre raster source or osmdroid | Forbidden | yes | Standard OSM style |
| osmdroid | Free (Apache-2.0) | n/a | n/a | View only (AndroidView) | Its own tile cache | Polyline overlay | Depends on tile source |
| Google Maps SDK + Maps Compose | $0, unlimited | Billing account + API key | Allowed | Official Maps Compose (mature) | Caching forbidden | `Polyline` composable | Built-in terrain map type |

## Findings per option

### MapLibre Native for Android (the renderer)

- **[verified]** Open source under the BSD 2-Clause licence. Described as "a free and open-source library for publishing maps in your apps". No API key is needed to use the library itself. ([GitHub README](https://github.com/maplibre/maplibre-native))
- **[verified]** The Maven artefact is `org.maplibre.gl:android-sdk`. The latest stable release on Maven Central is 13.6.1. ([Maven Central metadata](https://repo1.maven.org/maven2/org/maplibre/gl/android-sdk/maven-metadata.xml), [API docs](https://maplibre.org/maplibre-native/android/api/))
- **[verified]** The API has `org.maplibre.android.offline` (offline packs), `annotations` and `style.layers` packages. ([API docs](https://maplibre.org/maplibre-native/android/api/))
- **[verified]** Since 11.7.0 it can read PMTiles archives through `pmtiles://https://…` or `pmtiles://file://…`. That source type also works for `raster-dem`. PMTiles sources "do not support offline pack downloads or caching", and `pmtiles://asset://` is not supported. ([PMTiles example](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/))
- **[inference]** MapLibre Native is only a renderer, so any style or tile source in the MapLibre/Mapbox style spec can be plugged in. Choosing it does not tie the app to any one tile provider.

### MapLibre Compose (Compose integration)

- **[verified]** "A Compose Multiplatform wrapper around the MapLibre SDKs". Android support is **Beta**, and "minor releases can contain breaking changes". Licensed BSD-3-Clause. ([GitHub](https://github.com/maplibre/maplibre-compose))
- **[verified]** Artefact `org.maplibre.compose:maplibre-compose`, current version 0.19.0. ([Getting started](https://maplibre.org/maplibre-compose/getting-started/), [Maven Central](https://repo1.maven.org/maven2/org/maplibre/compose/maplibre-compose/maven-metadata.xml))
- **[verified]** Lines are drawn declaratively with `rememberGeoJsonSource(...)` plus `LineLayer(id, source, color, width)`. ([Layers guide](https://maplibre.org/maplibre-compose/layers/))
- **[verified]** Offline packs (`OfflineManager.create` / `resume` with a style URL and bounds) work on Android. The docs do not cover tile-provider terms. ([Offline guide](https://maplibre.org/maplibre-compose/offline/))
- **[inference]** Three candidate routes map naturally to three line layers, or to one layer styled per feature, with the chosen one highlighted. If the beta API churns too much, the fallback is wrapping the plain MapLibre `MapView` in `AndroidView`, which is more boilerplate but uses the stable SDK API.

### OpenFreeMap (free vector tiles)

- **[verified]** "There's no registration, no user database, no API keys, and no cookies", and there are "no limits on the number of map views or requests". Commercial use is allowed. Required attribution: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap". ([openfreemap.org](https://openfreemap.org/))
- **[verified]** Style URLs include `https://tiles.openfreemap.org/styles/liberty`, `/bright` and `/positron`. "For mobile apps, you can use the same styles with MapLibre Native." ([Quick start](https://openfreemap.org/quick_start/))
- **[verified]** The service is provided "as-is", can be discontinued at any time, and users must not "attempt to collect data from the service in automated ways without permission". ([ToS](https://openfreemap.org/tos/))
- **[verified]** The `liberty` style uses the `openmaptiles` vector source plus `ne2_shaded`, a Natural Earth shaded-relief raster. It has pedestrian-path layers (`road_path_pedestrian` and others) and no contour or hillshade layers. (Checked by fetching `https://tiles.openfreemap.org/styles/liberty` on 2026-10-03.)
- **[inference]** The Natural Earth relief only shows at low zoom, so at running zoom there is no terrain shading. Elevation would have to come from elsewhere, and the route's incline is a routing concern anyway, not a display one.
- **[inference]** Downloading an offline pack of a small home region for personal use is probably fine. Shipping offline downloads in a published app would need permission because of the ToS "automated ways" clause. OpenFreeMap also publishes full planet MBTiles downloads for self-hosting.

### Protomaps

- **[verified]** Hosted API: "Noncommercial use is free; for commercial use, become a GitHub Sponsor." An API key is required. ([protomaps.com](https://protomaps.com/))
- **[verified]** A single-file PMTiles basemap can be extracted for a region with the CLI and served as a static file. ([protomaps.com](https://protomaps.com/))
- **[inference]** A Sweden or home-region `.pmtiles` file stored on the phone (`pmtiles://file://`) gives fully offline maps with no server and no third-party terms beyond the ODbL attribution for OSM data. The cost is extra complexity: building the file, and providing a style, glyphs and sprites (Protomaps hosts these on GitHub Pages). This is overkill for v1.

### MapTiler Cloud (free plan)

- **[verified]** 5k map sessions and 100k API requests per month. Meant for "testing, PoC, prototyping, personal, or non-commercial use". No credit card is needed, but an account is. When the quota runs out the "service will pause until the next month". MapTiler branding is shown. ([Pricing](https://www.maptiler.com/cloud/pricing/))
- **[inference]** That is ample for one runner, but not publishable without a paid plan. Its Outdoor style is the closest free option to a "trail map" in MapLibre vector form.

### Stadia Maps (free plan)

- **[verified]** 200,000 credits per month with no overage, "Commercial use not allowed", account required, no credit card. ([Pricing](https://stadiamaps.com/pricing/))
- **[verified]** Free tier is "only for non-commercial or evaluation purposes". Mobile apps may cache data for offline use, "not to exceed 100MB cached at a time per device". ([ToS](https://stadiamaps.com/terms-of-service/))
- **[unverified]** Whether the Outdoors style is included on the free tier. The docs page returned 403.

### Thunderforest (Hobby plan)

- **[verified]** 150,000 tile requests per month, API key and account required. Styles include OpenCycleMap, Outdoors and Landscape. ([Pricing](https://www.thunderforest.com/pricing/))
- **[verified]** "We offer a free plan at our discretion" and may "modify or withdraw" it. Tiles "may be cached in-browser and on-device for offline use". Attribution goes to Thunderforest and OpenStreetMap contributors. ([Terms](https://www.thunderforest.com/terms/))
- **[inference]** These are raster tiles, which MapLibre can show as a raster source. Outdoors and Landscape show contours and hillshade, so this is the best free terrain-detail option for personal use.

### OSMF tile servers (`tile.openstreetmap.org`)

- **[verified]** "Our tile servers are not [free]: they are funded by donations and sponsorship, and capacity is limited." Apps must send a unique User-Agent, and generic ones such as `okhttp` are blocked. Tiles must be cached for at least 7 days. "Offline use is not permitted", and prefetching is forbidden. There is "no SLA or guarantee", and access "may be blocked without prior notice". ([Tile usage policy](https://operations.osmfoundation.org/policies/tiles/))
- **[inference]** Usable for a personal prototype, but fragile and a poor basis for a published app.

### osmdroid

- **[verified]** The repository was archived on 2024-11-20 ("the effort to maintain this project has become untenable"). The last release is 6.1.20 (2024-08-18), under Apache-2.0. ([GitHub](https://github.com/osmdroid/osmdroid))
- **[inference]** Do not build a new app on it. It is also View-based, with no Compose API.

### Google Maps SDK for Android + Maps Compose

- **[verified]** The "Maps SDK" SKU has an **Unlimited** free usage cap. ([Pricing](https://developers.google.com/maps/billing-and-pricing/pricing)) However, "you must enable billing on each of your projects and include an API key". ([Usage and billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing))
- **[verified]** Maps Compose is the official Compose library and has a `Polyline` composable. It requires an API key. Latest version: `com.google.maps.android:maps-compose` 9.0.0. ([Maps Compose](https://developers.google.com/maps/documentation/android-sdk/maps-compose), [Maven Central](https://repo1.maven.org/maven2/com/google/maps/android/maps-compose/maven-metadata.xml))
- **[verified]** "Customer will not cache Google Maps Content except as expressly permitted". There is also the "No Use With Non-Google Maps" rule. ([GMP Terms](https://cloud.google.com/maps-platform/terms))
- **[inference]** Displaying the map costs $0, but a billing account (with a payment method) is a standing exposure under the project's "no paid services" constraint, and there is no offline use. The non-Google-maps rule targets Google *content* such as Places and Directions, not our own route polylines on a Google map. This is the most polished developer experience, but it does not fit the constraint well.

## Open points for later tickets

- Whether the runner needs terrain detail (contours or hillshade) on the phone map. This decides whether a keyed outdoor style is worth adding.
- Whether offline maps are needed at all. The candidate routes are chosen at home, and the watch does the navigation.
