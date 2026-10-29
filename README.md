# android-tv-home

English · [Türkçe](README.tr.md)

A calm, tvOS-style home screen for Android TV, with your Philips Hue lights built in.
No ads, no sponsored rows, no account. Plain Java, no libraries, about 150 KB (56 KB of it the font), built without Gradle.

<p align="center">
  <img src="docs/home.jpg" width="49%" alt="Home in Halloween week: a glowing pumpkin by the greeting, a thin clock, the focused app's name over a red glow, the dock and the grid">
  <img src="docs/home-prime.jpg" width="49%" alt="The default theme with Prime Video focused: the glow turns blue">
  <img src="docs/menu.jpg" width="49%" alt="Hold OK on an app: a glass menu drops in from its card, the focused row white">
  <img src="docs/move.jpg" width="49%" alt="Moving an app: the card is lifted, the others dim, a hint says arrows move it and OK puts it down">
  <img src="docs/settings.jpg" width="49%" alt="Home settings in two panes: sections on the left, the themes on the right">
  <img src="docs/running-apps.jpg" width="49%" alt="Running apps: memory in use on top, Close all, and each app with Force stop">
  <img src="docs/lights.jpg" width="49%" alt="The lights panel slid in from the right, one card per Hue lamp in the lamp's color">
  <img src="docs/screensaver.jpg" width="49%" alt="Screensaver: a large thin clock, the date and the weather over a slow drifting glow">
</p>

## What it does

- **Home, the tvOS way.** A greeting over a thin clock, the date and the weather in small spaced capitals.
  The focused app's name stands large above the apps, and the room behind it takes that app's color: red for
  Netflix, blue for Prime Video. The first five apps sit in a dock band, the rest in a grid below. Focus springs
  up and settles, leans toward the way you moved and sends a glint across the banner; the cards rise into place
  when Home first appears. Everything is set in [Outfit](https://fonts.google.com/specimen/Outfit).
  The page glides with focus, and the clock and title fade as the grid comes up.
- **Themes.** Automatic follows the calendar: in Halloween week (Oct 24 to Nov 1) Home turns purple with an orange
  glow, a flickering jack-o'-lantern by the greeting and a colony of bats that flies over Home once a day. Or pick
  Default, Halloween, Midnight (pure black for OLED, no glow) or Aurora in Settings.
- **Your order.** Hold OK (or press Menu) on an app: a glass menu drops in from its card with **Move**, **Hide**,
  **Close**, **App info** and **Uninstall** (apps you installed). Move lifts the card and dims the others; the arrows
  carry it through the grid while the page holds still around it, OK puts it down.
- **Running apps.** Settings → Running apps lists what you used lately with the memory in use on top. **Close
  all** stops what plays and asks Android to end the apps; an app's row opens its own info page, where **Force
  stop** is one press. (Some TVs, this TCL among them, ignore an ordinary app's request to end another app;
  measured, so the sure way is the system's own Force stop.)
- **Home settings.** Two panes: the theme, the glow in the app's color, the greeting, Reduce motion, hidden apps
  (OK shows one again) and a reset of the app order.
- **Now Playing.** While anything plays (Spotify, YouTube, Stremio...), a card at the top shows the cover and
  title, tinted in the cover's color, with bars that move while it plays and a line that fills as the track
  goes. OK pauses or plays, Left and Right skip. A forgotten video in the background is one press away.
- **Hue lights one press away.** The Lights pill next to Settings slides the lights panel in from the right:
  one card per lamp, tinted with the lamp's real color; lamps cut off at the wall switch say so. OK switches a
  lamp; hold OK for brightness, colors, the 103 palettes of the Hue app's scenes, your own Hue scenes and the bridge's effects (candle, fireplace, opal...). Changes from the Hue app or
  a wall switch show up at once (the bridge's event stream).
- **Film mode.** Switch it on for the strip behind the TV. Opening an app changes nothing. Once a video has
  played for a few seconds, the strip takes the colors of what is playing (its cover art), made more vivid,
  drifting slowly along it with soft fades. Browsing previews and videos without a cover leave your color
  alone, and so are the room's other lamps. Paused or standby: the drift stops. Back on Home the strip,
  including its gradient or effect, returns to how it was.
- **The lights from anywhere.** One remote key (on TCL remotes the 123 key) slides the same panel in from the
  right, over whatever is playing. Android turns this key off if the app is force-stopped in Settings; run
  `./home.sh panel-key` again to bring it back.
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
- **Usage access** (`./home.sh install`), for *Running apps*: which apps were used lately and when. It stays on the TV.

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

## On slow TVs

Measured on the TCL this was built on (Realtek RTD288O, screen at 50 Hz, so 20 ms per frame): any frame of a
moving 1080p Home takes about 30 ms there, with or without the glow, the shadows or the lean, and the version
before this redesign measured the same. Animations run at 25 to 30 frames a second on such a set whatever the
home screen draws. Settings → Home → Reduce motion cuts the moves short and drops the lean and the spring;
there it took the median frame from 29 to 20 ms.

## Build and test

```bash
./home.sh build    # dist/android-tv-home.apk, signed with a local key in .signing/
./home.sh test     # the pure-Java parts (app order, colors, palettes, weather) on your machine's JVM
```

## Credits

Palette colors in `res/raw/hue_palettes.txt` come from
[labmonkey's dump of the Hue app scenes](https://gist.github.com/labmonkey/a641f6b339ed9a71bdced64b9af91ee8).
The Hue API details follow the [OpenHue API](https://github.com/openhue/openhue-api) specification. Weather by
[Open-Meteo](https://open-meteo.com). Ideas from [Luma](https://github.com/0xProbabillity/luma-tv),
[ScreenBloom](https://github.com/kershner/screenbloom) and [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
(the dock, the large title, the glow in the app's color, the dimmed move mode and the drop-in menu) and
[tvOS Launcher](https://github.com/techgenius1/tvOS-Launcher) (the two-pane settings and the running apps list). The font is [Outfit](https://github.com/Outfitio/Outfit-Fonts)
under the SIL Open Font License, see [FONT-LICENSE.txt](FONT-LICENSE.txt).

Companion: [android-tv-debloat](https://github.com/caferyukseloglu/android-tv-debloat) turns off the TV's ads
and background clutter.

## License

MIT
