# android-tv-home

A quiet home screen for Android TV: a clock, your Philips Hue lights, and your apps. No ads, no
sponsored rows, no tracking, no account. About 40 KB, plain Java, no libraries, built without Gradle.

- **Apps** as large cards, most recently used first. Hold OK on an app to hide it; hidden apps sit
  dimmed at the end, and holding OK again brings them back.
- **Hue lights** on the home screen. OK switches a lamp; hold OK for brightness (Left/Right) and colors.
  Cards take a tint of each lamp's real color, and lamps cut off at the wall switch say so.
- **Screen sync** for a Hue Play gradient lightstrip behind the TV: the strip follows the colors at
  the left, top and right edges of the picture. Hold OK on the strip and choose *Color with the picture*.
- Finds the Hue Bridge on its own (mDNS); pairing is one press of the bridge's button.

## Limits worth knowing

- **Screen sync goes dark on protected video.** Android hands apps a black frame for DRM content
  (Netflix, Prime Video, HBO Max and other paid streaming), so the strip holds its last colors there.
  It follows everything else: YouTube, Twitch, Kick, Stremio, games, the home screen.
- Screen sync uses the bridge's local HTTPS API, about 8 updates a second: smooth for ambient light,
  a little behind on fast cuts. Hue's Entertainment streaming (25+ updates a second) needs DTLS,
  which Android does not ship.
- The bridge's certificate is trusted on first use and pinned after that.

## Install

You need a Mac or Linux machine with `adb`, JDK 17 and the Android SDK (build-tools 36.0.0,
platform android-34). On the TV, turn on network debugging (Settings → Device Preferences → About:
press *Build* 7 times, then Developer options).

```bash
adb connect 192.168.1.80:5555     # your TV's address; accept the prompt on the TV
./home.sh install                 # build, install, allow screen sync without a prompt
./home.sh home                    # make it the home screen
./home.sh restore                 # give the home screen back to the TV's own launcher
```

`./home.sh home` first asks Android to use this app for Home. Many TVs still force their own launcher,
so if Home does not change, it switches the stock launcher off with `pm disable-user` (kept installed,
with its data) and remembers it in `.local/` for `restore`.

## Build and test

```bash
./home.sh build    # dist/android-tv-home.apk, signed with a local key in .signing/
./home.sh test     # the pure-Java parts (app order, color math) on your machine's JVM
```

## License

MIT
