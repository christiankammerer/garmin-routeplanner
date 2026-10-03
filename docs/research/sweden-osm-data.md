# OpenStreetMap and free elevation data in Sweden, per route preference

Research for [#5](https://github.com/christiankammerer/garmin-routeplanner/issues/5). The question: how well do OSM and free elevation data support each **route preference** (soft surface, low traffic, low incline, scenic) when generating **candidate routes** for a **route request** in Sweden.

Snapshot date: 2026-10-03. Tag statistics come from the Sweden-only taginfo instance run by Geofabrik (`https://taginfo.geofabrik.de/europe:sweden/`, data as of 2026-10-02), queried through its API (`/api/4/key/values`, `/api/4/tag/stats`, `/api/4/tag/combinations`).

**How to read the numbers:** taginfo counts OSM *ways* (segments), not kilometres. A short urban footway counts the same as a 3 km forest track. Treat the percentages as rough coverage signals, not length-weighted coverage. Each claim is marked **[verified]** (read directly from the cited source) or **[inference]** (my reading of the evidence, not stated by a source).

## Summary

| Route preference | Data source | Verdict |
|---|---|---|
| Soft surface | OSM `highway` + `surface` / `tracktype` | **Usable, with fallbacks.** The network of tracks and paths is dense. `surface` is set on only ~42–47% of path/footway/track ways, and much of it is the coarse `unpaved`. A router has to infer surface from `highway` type when the tag is missing. |
| Low traffic | OSM `highway` class + `maxspeed` | **Good.** `maxspeed` is on 83–99% of residential and higher roads. Road class plus speed is a solid proxy. No traffic volumes in OSM. Trafikverket has ÅDT traffic counts under CC0, but that means an extra data pipeline. |
| Low incline | DEM, not OSM | **Good.** Copernicus GLO-30 covers all of Sweden for free. SRTM does **not** cover north of 60°N. Lantmäteriet's 1 m terrain model is free (CC BY 4.0) but needs registration and heavy processing. OSM `incline` is almost never set (<1%). |
| Scenic | OSM land cover + protected areas + water | **Strong for nature, thin for "points of interest".** Forest, wetland and water are mapped nationwide, partly through a CC0 land-cover import. Nature reserves and parks are well represented. Viewpoints are sparse (~2.7k nationwide). |

## Soft surface

### Coverage evidence

Way counts by `highway` value in Sweden **[verified, taginfo Sweden]**:

| highway | ways | share of highway ways |
|---|---|---|
| service | 614,339 | 26.7% |
| track | 323,064 | 14.0% |
| path | 316,749 | 13.8% |
| footway | 273,289 | 11.9% |
| residential | 232,947 | 10.1% |
| unclassified | 203,790 | 8.9% |
| cycleway | 79,889 | 3.5% |
| bridleway | 1,907 | 0.1% |

Track, path and footway together make up ~40% of all highway ways. This is a dense network that runners can use **[verified counts; "dense" is inference]**.

Share of ways that also carry a surface-related tag **[verified, taginfo `tag/combinations`]**:

| highway | `surface` | `tracktype` | `smoothness` |
|---|---|---|---|
| path | 42.2% | 0.5% | 3.3% |
| footway | 40.3% | 0.1% | 3.0% |
| track | 46.5% | 23.1% | 3.3% |
| cycleway | 75.1% | 0.2% | 4.0% |
| unclassified | 58.3% | 1.5% | 1.7% |
| service | 34.5% | 0.4% | 1.0% |
| residential | 53.7% | 0.2% | 1.6% |
| tertiary and above | 84–95% | — | — |

Top `surface` values per highway type **[verified]**:

- `path`: asphalt 48k, ground 26k, dirt 15k, unpaved 13k, paved 6k, gravel 5k, grass 5k, fine_gravel 4k.
- `footway`: asphalt 60k, paving_stones 16k, fine_gravel 8k, gravel 5k.
- `track`: unpaved 111k, gravel 16k, compacted 6k, ground 4k, dirt 4k. Asphalt only 1k.
- `cycleway`: asphalt 49k, paved 5k.
- `unclassified`: unpaved 48k, asphalt 28k, gravel 23k.

Values of `tracktype` (83k ways in total): grade2 37%, grade3 26%, grade4 16%, grade5 12%, grade1 9% **[verified]**.

### Known gaps

- **More than half of paths and footways have no `surface` tag** **[verified]**. Those ways need a default guess. For example: untagged `path` = probably soft, untagged `footway` = probably paved in towns. This guess is **[inference]**. In Sweden, 48k of the tagged paths are asphalt, so `highway=path` alone does *not* reliably mean "trail". Many Swedish `path` ways are probably combined foot/cycle paths (`foot` and `bicycle` appear on ~28–29% of paths) **[verified counts; interpretation is inference]**.
- **`surface=unpaved` is the dominant value on tracks** (111k of 150k tagged tracks) and is the second most common value overall (283k ways) **[verified]**. It means "not paved" but gives no detail (gravel road vs. forest dirt). This is likely a result of imports from the NVDB road database, whose surface information is coarse. That is **[inference]**: the import page confirms NVDB supplies surface data, but I did not find a published per-value tag mapping. For a runner, gravel forest roads (`track` + `unpaved`/`gravel`) are "soft-ish". Real trails (`path` + `ground`/`dirt`) are softer. The data can only partly tell the two apart.
- **`smoothness` and `trail_visibility` are rare** (≤5%) **[verified]**. You cannot rely on them.
- Ground truth on fine surface (forest trail vs. maintained gravel path) is the weakest part of the data. A strength-based soft preference (not a hard rule) suits this level of data quality **[inference]**.

## Low traffic

### Coverage evidence

`maxspeed` coverage per road class **[verified, taginfo Sweden]**: trunk 99.0%, primary 99.4%, secondary 98.2%, tertiary 90.6%, residential 82.8%, unclassified 17.7%, service 5.2%, track 4.7%.

Most common `maxspeed` values (ways): 30 (24%), 70 (23%), 50 (21%), 40 (16%) **[verified]**. Sweden uses 30/40 km/h in urban zones and 70/80+ km/h on rural roads, so the speed limit separates quiet streets from busy roads well **[inference]**.

`sidewalk` is on only 6–14% of tertiary-to-trunk ways, and `lanes` on 40–94% **[verified]**. The `traffic` key is essentially unused (22 ways) **[verified]**.

### Traffic volume outside OSM

- Trafikverket's NVDB (Nationell vägdatabas) is published under **CC0** **[verified, OSM wiki "Import/Catalogue/Sweden highway import"]**. Its open API offers datasets that include `Hastighetsgräns` (speed limit), `FunktionellVägklass` (functional road class), `AntalKörfält2` (lanes) and `Vägbredd` (road width). Reading needs no login. Downloading needs a free account **[verified, Trafikverket news item, 2025]**.
- Trafikverket's *Trafik* data product contains **ÅDT** (annual average daily traffic) and is open data under CC0 1.0. It is available through Lastkajen (free registration) **[verified per Trafikverket product information as returned by search; I could not machine-read the full product specification PDF]**. I could **not** verify which roads have measured or modelled ÅDT. I expect the coverage to be densest on state roads and thin on municipal streets **[inference]**.

### Known gaps

- `unclassified` and `service` roads, which are many of the small rural roads a runner meets, mostly have **no** `maxspeed` (only 18% and 5% have it) **[verified]**. In Sweden, `unclassified` gravel roads are generally quiet. Using the road class alone is a reasonable proxy there **[inference]**.
- NVDB-based road classification in OSM is imperfect. The import page states the dataset "does not have a very granular distinction between tertiary/secondary/residential/service highways, so manual editing will sometimes be needed" **[verified]**.
- The community NVDB road import is a slow manual effort, one municipality at a time. As of 2025-08-30 the coordinator reported that file uploads "seem to have stopped" **[verified, OSM Community forum thread]**. So the quality of tags varies by municipality **[inference]**.
- Using ÅDT would mean joining Trafikverket road segments to OSM ways. NVDB and OSM segment roads differently: "OSM typiskt inte delar upp vägarna i samma segment som NVDB" **[verified, same forum thread]**. This is a non-trivial, offline preprocessing job. It conflicts with the "no own server" constraint unless done once and bundled **[inference]**.

## Low incline

### Elevation sources

| Source | Resolution | Coverage of Sweden | Licence / access | Notes |
|---|---|---|---|---|
| Copernicus DEM GLO-30 | 30 m | Whole country | Free and open. Public S3 bucket `copernicus-dem-30m`, no AWS account needed **[verified, AWS Open Data registry]** | A **DSM** (surface model): it includes vegetation and buildings **[verified]**. |
| SRTM 1 arc-second | ~30 m | **Only south of 60°N** **[verified, USGS]** | Public domain | Misses roughly the northern half of Sweden, which lies between ~55.3°N and ~69°N. Unsuitable as the only source **[inference from the extents]**. |
| Lantmäteriet *Markhöjdmodell Nedladdning, grid 1+* | 1 m | Nationwide (metadata extent 55.26°–68.83°N) **[verified, metadata record]** | **CC BY 4.0**, free of charge. Ordered through Geotorget (free account); also offered through a STAC API (`api.lantmateriet.se/stac-hojd/v1`) **[verified, metadata record + search results citing Lantmäteriet]** | A true **DTM** (ground surface from laser scanning). Made openly available as an EU High-Value Dataset in early 2025 **[verified per search-result summaries; not read on lantmateriet.se itself]**. |
| OSM `incline` tag | — | path 0.9%, footway 0.3%, track 0.1% **[verified]** | ODbL | Effectively absent. Ignore it. |

### Known gaps

- Copernicus is a surface model. In forest, which covers much of Sweden, the "elevation" includes the tree canopy. This adds noise to path gradients in woodland **[inference from DSM definition]**. At 30 m spacing, short steep pitches disappear. For a soft "low incline" preference measured in total ascent per route, that is probably acceptable **[inference]**.
- Lantmäteriet's 1 m DTM is far more accurate. But the national dataset is very large, and the app would need a preprocessing step that samples it along OSM ways. That again points at a one-off offline build, or a hosted service that already embeds a DEM **[inference]**. I have not checked whether any free routing service uses Lantmäteriet data. That question belongs to the routing-engine ticket.
- The CC BY 4.0 licence requires attribution in the app **[verified licence; obligation is the standard CC BY term]**.

## Scenic

### Coverage evidence

Element counts in Sweden **[verified, taginfo Sweden `tag/stats`]**:

| Feature | Count (all elements) |
|---|---|
| `landuse=forest` | 449,257 |
| `natural=wood` | 58,195 |
| `natural=wetland` | 291,502 |
| `natural=water` | 170,243 |
| `waterway=stream` | 132,348 |
| `waterway=river` | 17,570 |
| `natural=coastline` | 79,780 ways |
| `landuse=meadow` | 78,124 |
| `natural=bare_rock` | 30,523 |
| `leisure=park` | 9,392 |
| `leisure=nature_reserve` | 5,914 |
| `boundary=protected_area` | 2,712 |
| `boundary=national_park` | 50 |
| `tourism=viewpoint` | 2,693 (almost all nodes) |
| `route=hiking` relations | 3,099 |
| `route=foot` relations | 915 |

- The most common `source` value on ways is `NV NMD2018`, with 383,989 ways (34% of ways with a `source` tag) **[verified]**. This is the import of Naturvårdsverket's *Nationella Marktäckedata* 2018: a nationwide 10 m land-cover raster derived from Sentinel-2. It is licensed CC0 and produces `landuse=forest`, grassland and `natural=wetland` areas **[verified, OSM wiki NMD 2018 Import Plan]**. So land cover is mapped broadly in Sweden, not only where volunteers drew it **[inference]**.

### Known gaps

- The NMD import is uneven. The wiki status table lists only a handful of municipalities as "completed" and many as "Already well mapped" (cancelled) or not started **[verified, wiki status page]**. The table may be out of date, given the 384k imported ways. Some rural areas probably still have no land cover **[inference]**.
- Imported forest polygons are machine-classified, and the wiki notes some tags may need retagging (e.g. grassland → heath in mountain areas) **[verified]**. That is fine for a "near green/water" score. It is less fine for anything finer.
- **Viewpoints (2.7k) and named scenic POIs are sparse** **[verified count]**. A scenic score should rely on proximity to forest, water, coastline and protected areas rather than on POIs **[inference]**.
- "Scenic" is subjective. OSM can tell you *what is nearby*, not *what is pretty*. Hiking/foot route relations (~4k) are a useful extra signal that a path is worth running **[inference]**.

## Implications for the spec

All points here are **[inference]**:

1. All four route preferences can be supported from free data in Sweden. Soft surface and scenic come from OSM alone. Low traffic comes from OSM alone (road class + `maxspeed`). Low incline needs a DEM.
2. Soft surface is the preference with the weakest data. The weighting has to fall back to `highway` type when `surface` is missing, and should treat `unpaved` on tracks as "moderately soft".
3. For elevation, Copernicus GLO-30 is the simplest free source covering all of Sweden. Do not rely on SRTM-only tooling (e.g. elevation services built only on SRTM), because it has no data north of 60°N.
4. Trafikverket ÅDT and the Lantmäteriet 1 m DTM are quality upgrades, not requirements. Both need an offline data-preparation step, which conflicts with the "no own server" constraint unless it is done once and shipped with the app or provided by a third-party service.

## Sources

- Geofabrik taginfo, Sweden instance: https://taginfo.geofabrik.de/europe:sweden/ (API `/api/4/...`, data until 2026-10-02T20:15Z)
- OSM wiki, Sweden highway import (NVDB, CC0): https://wiki.openstreetmap.org/wiki/Import/Catalogue/Sweden_highway_import
- OSM Community forum, "Hjälp till med NVDB-vägimporten!": https://community.openstreetmap.org/t/hjalp-till-med-nvdb-vagimporten/112803
- OSM wiki, NMD 2018 Import Plan: https://wiki.openstreetmap.org/wiki/Import/Catalogue/NMD_2018_Import_Plan
- OSM wiki, NMD 2018 status per subarea: https://wiki.openstreetmap.org/wiki/Import/Catalogue/NMD_2018_Import_Plan/Status_per_subarea
- Trafikverket, NVDB data in the open API (2025): https://bransch.trafikverket.se/tjanster/data-kartor-och-geodatatjanster/nyheter-om-trafikverkets-data/2025/nvdb-vagdata-tillgangliga-i-trafikverkets-datautbytesportal-for-anvandning-i-oppet-api/
- Trafikverket, Trafik data product specification (ÅDT): https://bransch.trafikverket.se/TrvSeFiler/Dataproduktspecifikationer/V%C3%A4gdataprodukter/DPS_S-T/1050Trafik.pdf
- Trafikverket, open data download: https://www.trafikverket.se/e-tjanster/hamta-data-fran-trafikverket/
- Lantmäteriet, Markhöjdmodell Nedladdning grid 1+ metadata (CC BY 4.0, 1 m, extent): https://catalogue.arctic-sdi.org/geonetwork/srv/api/records/c510e63f-0bf3-46c9-ab8b-3b37a898af85
- Lantmäteriet, product page: https://www.lantmateriet.se/sv/geodata/vara-produkter/produktlista/markhojdmodell-nedladdning/
- Lantmäteriet, product description grid 1+: https://www.lantmateriet.se/globalassets/geodata/geodataprodukter/hojddata/mhm1_plus.pdf
- Copernicus DEM on AWS Open Data: https://registry.opendata.aws/copernicus-dem/
- USGS, SRTM mission summary (60°N–56°S coverage): https://www.usgs.gov/centers/eros/science/usgs-eros-archive-digital-elevation-srtm-mission-summary
