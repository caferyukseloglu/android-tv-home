# android-tv-home

A quiet home screen for Android TV: a clock, your Philips Hue lights, and your apps. No ads, no
sponsored rows, no tracking, no account. About 40 KB, plain Java, no libraries, built without Gradle.

- **Apps** as large cards, most recently used first. Hold OK on an app to hide it; hidden apps sit
  dimmed at the end, and holding OK again brings them back.
- **Hue lights** on the home screen. OK switches a lamp; hold OK for brightness (Left/Right), colors
  and the bridge's effects (candle, fireplace, opal, ...). Cards take a tint of each lamp's real color,
  and lamps cut off at the wall switch say so.
- **Film mode** for the light strip behind the TV: while a video app is open (Stremio, Netflix, YouTube,
  Prime Video, ...) the strip glows dim 6500 K white, the bias light video is mastered for, and goes
  back to how it was when you return Home. Hold OK on the strip and switch *Film mode* on.
- Finds the Hue Bridge on its own (mDNS); pairing is one press of the bridge's button.

## Why not color the strip from the picture?

That was the first plan, and it does not work on TVs like this one: video plays on a hardware plane
that never reaches Android's compositor, so any screen capture, even `adb shell screencap`, gets black
while a video plays. Film mode needs no capture. For true picture-following light on a TV's own apps,
route the content through HDMI (a streaming box into a Hue Sync Box).

## Install

You need a Mac or Linux machine with `adb`, JDK 17 and the Android SDK (build-tools 36.0.0,
platform android-34). On the TV, turn on network debugging (Settings → Device Preferences → About:
press *Build* 7 times, then Developer options).

```bash
adb connect 192.168.1.80:5555     # your TV's address; accept the prompt on the TV
./home.sh install                 # build and install
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
