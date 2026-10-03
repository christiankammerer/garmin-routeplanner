# Free place search for start and finish points

Research for [#12](https://github.com/christiankammerer/garmin-routeplanner/issues/12) (map: [#1](https://github.com/christiankammerer/garmin-routeplanner/issues/1)).
Researched 2026-10-03 against primary sources (official usage policies, API docs, pricing pages, source READMEs), plus
a handful of live test queries (listed below).

**Question:** which free place-search (geocoding) service can the Android app use so the runner can search for a
**start** or **finish** point in Sweden, with no cost, no own server and no licence fee? Compared on Swedish
place/address/POI coverage, autocomplete suitability, rate limits and policy (personal app and later published app),
API key needs and attribution.

Legend: **[V]** verified in the cited primary source (or by a live query, marked "live"). **[I]** inference from the
sources. **[U]** unverified / could not be confirmed.

## Summary

| | Photon (komoot public server) | Nominatim (OSMF public server) | Android `Geocoder` (Google-backed on Pixel) | Geoapify Free | LocationIQ Free | Lantmäteriet (Belägenhetsadress Direkt) | On-device index (own build) |
|---|---|---|---|---|---|---|---|
| Data | OSM [V] | OSM [V] | Google [I] | OSM + others [U] | OSM [U] | Official Swedish addresses only [V] | OSM extract and/or Lantmäteriet [I] |
| Autocomplete / prefix search | Yes, "search-as-you-type" [V]; "slottssko" → Slottsskogen (live) | **Banned** by policy [V]; no prefix match ("slottssko" → no results, live) | No prefix search; one lookup per query [I] | Yes, Autocomplete API [V] | Yes [V] | No [U] | Yes, if built (e.g. SQLite FTS) [I] |
| POIs, parks, areas | Yes (parks, sports centres, suburbs, live) [V] | Yes (live) [V] | Partial [U] | Yes [U] | Yes [U] | No (addresses only) [V] | Whatever is indexed [I] |
| Country filter / location bias | `countrycode=SE`, `lat`/`lon` bias, `bbox`, `osm_tag`, `layer` [V] | `countrycodes=se`, `viewbox` [V] | bounding box overload [V] | `filter=countrycode:se`, `bias` [V] | yes [U] | n/a | own [I] |
| Rate limit | "reasonable limit"; extensive use throttled or banned [V] | absolute max 1 req/s, must cache [V] | none published [U] | 3,000 credits/day, 5 req/s; 1 credit per request [V] | 5,000/day, 2 req/s, 60/min [V] | [U] | none [I] |
| API key / account | None [V] | None; must send identifying User-Agent [V] | None [V] | Key + account [V] | Token + account [V] | Account + key in Lantmäteriet API portal [V/U] | None [I] |
| Personal use | OK [V] | OK on explicit search [V] | OK [I] | OK [V] | OK [V] | OK [I] | OK [I] |
| Published app | Allowed but no guarantee; can be throttled/banned without notice [V] | Allowed only with switchable endpoint, no autocomplete [V] | OK [I] | Commercial OK on Free, with "Powered by Geoapify" [V]; one key shared by all users [I] | Commercial OK on Free with prominent link [V]; one token shared [I] | Key shipped in app [I] | OK; big download [I] |
| Attribution | © OpenStreetMap contributors (ODbL) [V/I] | OSM attribution required [V] | none required [I] | "Powered by Geoapify" [V] + OSM [I] | "Search by LocationIQ.com" link [V] + OSM [I] | CC BY 4.0 → credit Lantmäteriet [V/U] | ODbL / CC BY [I] |

**Short recommendation (the decision belongs to its own ticket):** use **Photon's public server** for the search box:
it is the only key-less free option built for search-as-you-type, returns parks, sports centres, suburbs, streets and
house numbers in Sweden, and can be restricted to Sweden (`countrycode=SE`) and biased to the runner's position
(`lat`/`lon`). Keep requests polite (debounce, minimum query length, cache) and make the endpoint URL configurable so it
can be switched to another Photon instance or provider without an app update. **Runner-up / fallback:** Android's
built-in `Geocoder` on explicit submit (no key, Google data on a Pixel, likely the best house-number coverage), or
Nominatim on explicit submit only. If the app is published and Photon's public server becomes unreliable, **Geoapify
Free** (keyed, commercial OK with attribution, 3,000 requests/day shared by all users) is the cleanest hosted switch.

## Photon (komoot public server, photon.komoot.io)

- **What it is** [V]: open-source geocoder for OSM data, Apache-2.0; komoot runs the public demo server
  ([README](https://github.com/komoot/photon/blob/master/README.md)). Features include "search-as-you-type", "location
  bias", "typo tolerance", "filter by osm tag and value", "filter by bounding box", reverse geocoding.
- **Usage policy** [V] (README, "Demo server"): "You are welcome to use the API for your project as long as the number
  of requests stay in a reasonable limit. Extensive usage will be throttled or completely banned. We do not give
  guarantees for availability and reserve the right to implement changes without notice." The site
  ([photon.komoot.io](https://photon.komoot.io/)) says the same: "please be fair - extensive usage will be throttled".
  No numeric limit, no key, no account, nothing about commercial use [V, by absence].
- **API** [V] ([api-v1.md](https://github.com/komoot/photon/blob/master/docs/api-v1.md)): `q`, `limit`, `lang`,
  `lat`/`lon` with `zoom` and `location_bias_scale`, `bbox`, `countrycode`, `osm_tag` include/exclude filters, `layer`
  (e.g. `house`, `street`, `locality`, `district`, `city`), `dedupe`, structured search, `/reverse`. Results are GeoJSON.
- **Live checks (2026-10-03)** [V, live]: with `lat=59.33&lon=18.06`:
  - "Drottninggatan 53, Stockholm" → the house, Stockholm.
  - "Skatås motionscentrum" → the sports centre and the building, Göteborg.
  - "Hagaparken" → park + wood, Solna; with `countrycode=SE&lat=57.70&lon=11.97` the Göteborg Hagaparken ranks first.
  - "Vasagatan 1 Göteborg" → the house.
  - "slottssko" (incomplete word) → Slottsskogen, Slottsskogsleden: prefix matching works.
  - "Ålidhem Umeå" → the suburb.
  - Response header `Cache-Control: max-age=3600`; no rate-limit headers seen.
- **Attribution** [V/I]: data is OSM (ODbL), so the app must credit OpenStreetMap; the OSMF guidelines say applications
  that use a geocoder "must credit OpenStreetMap", but individual results need no attribution
  ([attribution guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)) [V]. The map (OpenFreeMap)
  already shows OSM attribution [I], which covers this.
- **Self-hosting is out** [V/I]: a planet database needs ~95 GB disk and ≥64 GB RAM (README); country dumps exist
  (GraphHopper weekly dumps) but still need a server running Java + OpenSearch, which the constraints exclude [I].
- **Risk** [I]: one shared public endpoint with no SLA. Fine for one runner. For a published app, many users could be
  throttled as one client; hence the configurable endpoint and a fallback.

## Nominatim (OSMF public server, nominatim.openstreetmap.org)

- **Usage policy** [V] ([policy](https://operations.osmfoundation.org/policies/nominatim/)):
  - "an absolute maximum of 1 request per second".
  - "Provide a valid HTTP Referer or User-Agent identifying the application (stock User-Agents as set by http libraries
    will not do)."
  - **"Auto-complete search** This is not yet supported by Nominatim and you must not implement such a service on the
    client side using the API."
  - "Results must be cached on your side."
  - "Clearly display attribution as suitable for your medium."
  - "Apps must make sure that they can switch the service at our request at any time (in particular, switching should
    be possible without requiring a software update)."
  - The policy "may change without notice"; commercial apps should keep that in mind.
- **Live checks** [V, live] (`countrycodes=se`): finds the same houses, sports centre, parks (both Hagaparken) and Ålidhem
  as Photon, with rich `display_name`; "slottssko" returns **nothing** (no prefix search).
- **Fit** [I]: usable only as a submit-to-search (press Enter/search) box, not type-ahead. Fine as a fallback; the
  switchable-endpoint rule is the same one recommended for Photon anyway.

## Android `android.location.Geocoder`

- **API** [V] ([reference](https://developer.android.com/reference/android/location/Geocoder)):
  `getFromLocationName(name, maxResults, [bounding box], GeocodeListener)` (listener variants since API 33; the
  blocking variants are deprecated). `isPresent()` "Returns true if there is a geocoder implementation present on the
  device"; otherwise "any attempt to geocode will result in an error". Results are `null`/empty "if no matches were
  found or there is no backend service available".
- **Caveat in the docs** [V]: "Geocoding services may provide no guarantees on availability or accuracy. Results are a
  best guess, and are not guaranteed to be meaningful or correct."
- **Backend** [I]: on a Pixel 8 with Google Play services the implementation is Google's; no key or account. Google
  publishes **no quota and no app-facing terms** for this path [U]. Developers report intermittent
  "Service not Available" / "grpc failed" `IOException`s ([Google issue tracker](https://issuetracker.google.com/issues/36906463)) [V, reports].
- **Fit** [I]: no prefix/type-ahead behaviour and an `Address` result with little place-type information, so it is weak
  as the main search UX, but likely the best coverage of Swedish house addresses (Google data; see the OSM address gap
  below). Good as an on-submit fallback. Not present on de-Googled devices (matters only if published).

## Other hosted free tiers (all need an account and a key shipped in the app)

- **Geoapify Free** [V] ([pricing](https://www.geoapify.com/pricing/),
  [autocomplete docs](https://apidocs.geoapify.com/docs/geocoding/address-autocomplete/)): 3,000 credits/day, up to
  5 requests/s, no credit card; "You can use the Free plan for commercial websites, apps, and business projects";
  attribution "Powered by Geoapify" required. Autocomplete is 1 credit per request; `filter=countrycode:se`, `bias`,
  `lang`. [I] At ~5–10 keystroke requests per search that is ~300–600 searches/day for *all* users of a published app.
  Best keyed option.
- **LocationIQ Free** [V] ([pricing](https://locationiq.com/pricing)): 5,000 requests/day, 2/s, 60/min; autocomplete
  included; commercial use allowed only with a prominent "Search by LocationIQ.com" link back. [U] Its Sweden data
  source (OSM-based) was not checked.
- **OpenCage** [V] ([pricing](https://opencagedata.com/pricing)): free trial is 2,500/day, 1/s, **testing only**;
  "If you decide to use our service in production you should become a paying customer"; autosuggest is paid only.
  Rejected.
- **MapTiler Cloud Free** [V] ([pricing](https://www.maptiler.com/cloud/pricing/)): only 1k search sessions/month, and
  "service will pause until the next month" when exceeded. Rejected (also adds a key and a second vendor next to
  OpenFreeMap).

## Lantmäteriet open data (official Swedish addresses)

- **Free since 2025** [V] ([announcement](https://www.lantmateriet.se/sv/geodata/vara-produkter/Produktnyheter/Allmanna-nyheter/vardefulla-datamangder---forandringar-pa-gang/)):
  "Från den 3 februari blir nedladdningsprodukter och direkttjänster för datamängder som pekas ut av EU-direktivet för
  HVD avgiftsfria", including Belägenhetsadress (download and direct API) and Ortnamn (download).
- **Access** [V/U]: the direct APIs are managed through Lantmäteriet's
  [API portal](https://www.lantmateriet.se/sv/geodata/vara-produkter/produktsupport/api-portalen/), which issues
  authorization keys to logged-in accounts; exact terms for embedding a key in a public app were not found [U]. Licence
  of the address points is CC BY 4.0 according to the OSM import plan (below) [V, secondary] / [U] on Lantmäteriet's own page.
- **Fit** [I]: addresses only (no parks, trails, sports centres), no confirmed free-text/type-ahead search. Not a good
  primary search; at most a later precise-address add-on.

## On-device / offline options

- **Own index over a Sweden extract** [I]: Geofabrik's `sweden-latest.osm.pbf` is 809 MB, updated daily, ODbL
  ([Geofabrik](https://download.geofabrik.de/europe/sweden.html)) [V]. A names+addresses index (e.g. SQLite FTS5) would
  have to be built offline, hosted somewhere free (e.g. GitHub Releases) and downloaded by the app; likely 100+ MB, plus
  writing the build pipeline, ranking and updates. No mature Kotlin/Android offline geocoder library was found [U].
  Organic Maps/OsmAnd have offline search, but tied to their own map formats and code bases [I]. Too much effort for v1;
  only worth it if offline search becomes a requirement.
- **BRouter segments** [I]: routing data only; no names, so no help for search.
- **Android `Geocoder`** is not offline (network backend) [I].

## Swedish coverage notes (OSM-based services)

- **Addresses are the weak spot** [V] ([Sweden Address Import](https://wiki.openstreetmap.org/wiki/Import/Catalogue/Sweden_Address_Import)):
  Lantmäteriet's open dataset has 3.7 million addresses, while OSM had ~830k Swedish addresses (March 2025), many
  incomplete. An import (`addr2osm`) is planned but, as of the page's April 2026 revision, still under development with
  Lantmäteriet's permission pending. [I] So house numbers in rural areas and smaller towns may be missing in
  Photon/Nominatim; city-centre addresses tested fine (live).
- **Places, parks, POIs** [V, live]: parks, woods, sports centres, suburbs and streets were found in Stockholm, Göteborg
  and Umeå. For a runner choosing a start/finish, these matter more than house numbers [I]; and the map pin / current
  location / saved place cover the rest (per #10 prototype).

## Suggested integration (inference, for the spec)

- Photon: `GET https://photon.komoot.io/api?q=…&limit=5&countrycode=SE&lat=…&lon=…&lang=default` [V params];
  debounce ~300 ms, start at 3 characters, cancel in-flight calls, cache recent queries, send an identifying
  User-Agent [I].
- Endpoint URL configurable (remote config or settings) so it can be switched without an app update, as Nominatim's
  policy requires and as a hedge for Photon [I].
- On submit with no good result: fall back to `Geocoder` (if `isPresent()`) [I].
- Show "© OpenStreetMap contributors" in the app (already needed for the map) [V/I].

## Live test queries

Run 2026-10-03 with a script sending ≤1 request per 1.5 s and an identifying User-Agent, 6 queries per service:
"Drottninggatan 53, Stockholm", "Skatås motionscentrum", "Hagaparken", "Vasagatan 1 Göteborg", "slottssko",
"Ålidhem Umeå"; plus one Photon query "hagaparken" with `countrycode=SE` and Göteborg bias. Android `Geocoder` was not
tested (needs a device).
