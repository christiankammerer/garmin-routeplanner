# Garmin Route Planner

Generates running routes on a phone from a few wishes (distance, start, preferences) and gets them onto a Garmin watch to run them.

## Planning a route

**Route request**:
What the runner asks for: a target distance, a start point, an optional end point, and route preferences.
_Avoid_: Query, search, settings

**Target distance**:
The shortest the runner wants the route to be; a generated route may run a little longer, never shorter.
_Avoid_: Length, goal

**Route preference**:
A wished-for characteristic of a route (soft surface, scenic, low traffic, low incline) that the runner switches on or off; when on, it steers route generation without ruling any way out.
_Avoid_: Characteristic, filter, constraint

**Scenic**:
The route preference for running among forest, parks and water, along signed hiking or walking routes, and away from loud roads and industrial areas.
_Avoid_: Nice, beautiful, green

**Low traffic**:
The route preference for running on small, slow roads and paths, away from heavy or fast vehicle traffic, for safety rather than for scenery.
_Avoid_: Quiet, safe

**Route**:
One generated path on the phone side, satisfying a route request.
_Avoid_: Track, path, course

**Loop route**:
A route whose end point is its start point; the default when no end point is given.
_Avoid_: Round trip, circuit

**Point-to-point route**:
A route from the start point to a distinct end point.
_Avoid_: One-way route, A-to-B

**Candidate route**:
One of the few routes generated for a single route request, from which the runner picks one.
_Avoid_: Option, suggestion, alternative

**Saved place**:
A named location the runner kept in the phone app to use as a start or end point; a shortcut for filling in a route request, which saved routes never refer back to.
_Avoid_: Favourite, bookmark, location

**Saved route**:
A route the runner kept in the phone app to run again, unchanged from when it was saved.
_Avoid_: Favourite, bookmark

## On the watch

**Course**:
A route as it exists on the watch, in a form the watch can navigate.
_Avoid_: Route (on the watch side), track, workout

**Off-route alert**:
The watch's warning that the runner has left the course.
_Avoid_: Deviation warning

**Turn prompt**:
The watch's heads-up, shortly before a turn on the course, saying which way to go.
_Avoid_: Turn-by-turn, voice hint, instruction

**Distance remaining**:
How far along the course the runner still has to go to its end, measured along the course rather than in a straight line.
_Avoid_: Distance to go, distance to destination
