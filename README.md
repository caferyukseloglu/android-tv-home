# android-tv-home

A calm, tvOS-style home screen for Android TV, with your Philips Hue lights built in.
No ads, no sponsored rows, no account. Plain Java, no libraries, about 70 KB, built without Gradle.

<p align="center">
  <img src="docs/home.jpg" width="49%" alt="Home: date and weather, a Now Playing card, Hue lights as the top shelf, apps in a five-column grid">
  <img src="docs/screensaver.jpg" width="49%" alt="Screensaver: a large thin clock, the date and the weather over a slow drifting glow">
</p>

## What it does

- **Home, the tvOS way.** A quiet header (date, weather, time), your apps as a five-column grid with the
  name under the focused card only. Focus springs up and settles, leans toward the way you moved and sends a
  glint across the banner; the cards rise into place when Home first appears.
- **Your order.** Hold OK on an app for **Move**, **Hide** or **Uninstall** (apps you installed). Move
  carries the card through the grid with the arrows; new apps join at the end.
- **Now Playing.** While anything plays (Spotify, YouTube, Stremio...), a card at the top shows the cover and
  title. OK pauses or plays, Left and Right skip. A forgotten video in the background is one press away.
- **Hue lights as the top shelf.** One card per lamp, tinted with the lamp's real color; lamps cut off at the
  wall switch say so. OK switches a lamp; hold OK for brightness, colors, the 103 palettes of the Hue app's
  scenes, your own Hue scenes and the bridge's effects (candle, fireplace, opal...). Changes from the Hue app or
  a wall switch show up at once (the bridge's event stream).
- **Film mode.** Switch it on for the strip behind the TV. Opening an app changes nothing. Once a video has
  played for a few seconds, the strip takes the colors of what is playing (its cover art), made more vivid,
  drifting slowly along it with soft fades. Browsing previews and videos without a cover leave your color
  alone, and so are the room's other lamps. Paused or standby: the drift stops. Back on Home the strip,
  including its gradient or effect, returns to how it was.
- **The lights from anywhere.** One remote key (on TCL remotes the 123 key) slides a lights panel in from the
  right, over whatever is playing.
- **Screensaver.** A large thin clock, the date and the weather over a slowly drifting glow; the text moves
  every minute so nothing burns in.
- Finds the Hue Bridge on its own (mDNS); pairing is one press of the bridge's button.

## Why the lights follow the cover, not the picture

The first plan was true screen sync. On TVs like the TCL this was built on, it cannot work: video plays on a
hardware plane that Android's screen capture never sees (any capture, even `adb shell screencap`, is black
where the video is), and the TV maker's own capture service only answers apps signed with its platform key.
So film mode uses what Android does share, the now-playing cover. For live, scene-by-scene light, route the
picture through HDMI into a Hue Sync Box.

## What it sends and what it can see

Nothing goes to us; there is no server of ours. The app talks to:

| Where | What | When |
| --- | --- | --- |
| Your Hue Bridge (your network) | Light commands and the bridge's event stream | While Home or the lights panel is open; while a video plays in film mode |
| `api.open-meteo.com`, `geocoding-api.open-meteo.com` | Approximate coordinates (two decimals, about 1 km) or your time zone's city | Weather, at most every 30 minutes |
| `ip-api.com` (plain HTTP) | Your network's public IP, to find a coarse place | Once, only if the TV's time zone has no city (e.g. `GMT+03:00`) |
| Cover image addresses from your video apps | A normal image download | When a video app gives its cover as a link |

Two permissions are granted by the setup commands, and you can turn both off in Android's settings:

- **Notification access** (`./home.sh install`), for *Film mode colors*: it reads the playing app's media
  session (title and cover) for film mode and Now Playing. It reads no notifications.
- **Accessibility** (`./home.sh panel-key`), for *Lights key*: it receives key presses so one key can open the
  lights panel over other apps. It reads nothing on screen.

`./home.sh home` also makes this app's clock the screensaver.

## Install

You need a Mac or Linux machine with `adb`, JDK 17 and the Android SDK (build-tools 36.0.0,
platform android-34). On the TV, turn on network debugging (Settings → Device Preferences → About:
press *Build* 7 times, then Developer options).

```bash
adb connect 192.168.1.80:5555     # your TV's address; accept the prompt on the TV
./home.sh install                 # build, install, allow Now Playing / film mode to read the media session
./home.sh home                    # make it the home screen and the screensaver
./home.sh panel-key               # optional: one remote key opens the lights panel from anywhere
./home.sh restore                 # give the home screen back to the TV's own launcher
```

`./home.sh home` first asks Android to use this app for Home. Many TVs still force their own launcher, so if
Home does not change, it switches the stock launcher off with `pm disable-user` (kept installed, with its data)
and remembers it in `.local/` for `restore`.

## Build and test

```bash
./home.sh build    # dist/android-tv-home.apk, signed with a local key in .signing/
./home.sh test     # the pure-Java parts (app order, colors, palettes, weather) on your machine's JVM
```

## Credits

Palette colors in `res/raw/hue_palettes.txt` come from
[labmonkey's dump of the Hue app scenes](https://gist.github.com/labmonkey/a641f6b339ed9a71bdced64b9af91ee8).
The Hue API details follow the [OpenHue API](https://github.com/openhue/openhue-api) specification. Weather by
[Open-Meteo](https://open-meteo.com). Ideas from [Luma](https://github.com/0xProbabillity/luma-tv) and
[ScreenBloom](https://github.com/kershner/screenbloom).

Companion: [android-tv-debloat](https://github.com/caferyukseloglu/android-tv-debloat) turns off the TV's ads
and background clutter.

## License

MIT
