# Persona Android 6 experiment

This branch adapts DiPlay for a 2019 Proton Persona Premium factory head unit
running Android 6.0.1 (API 23). It is an experimental source branch, not a
supported release or a ready-to-install APK.
This independent community experiment is not affiliated with, authorized by,
or endorsed by Proton. It is not an official Proton Persona product.

## Observed and unverified behavior

On one car, a locally built wired test package entered CarPlay and played music.
The source includes Android 6 USB, TLS, and audio compatibility changes. This
does not establish compatibility with other Persona firmware, iPhones, or head
units. Calls, Siri, prolonged use, and switching between multiple iPhones need
further testing.

The wireless changes in this branch are exploratory. The intended first route
uses an external Wi-Fi network shared by the head unit and iPhone. Bluetooth
access, iAP2 connection, IPv6 reachability, and end-to-end wireless CarPlay have
not been confirmed on the Persona. The wireless source is provided for review
and testing; it is not a report of successful wireless use.

## Source and authentication

The repository excludes accessory authentication material and Android signing
keys. An ordinary source build has no accessory identity and cannot be treated
as a working CarPlay release. The original project's [build instructions](BUILD.md)
describe an optional external asset input for local builds. Material supplied
for local testing has not been cleared for public redistribution, including in
an APK. Do not add that material, generated APKs, device logs, pairing records,
or phone identifiers to this branch.

This branch retains the upstream project's license and third-party notices.
Some existing repository assets have separate terms, including Apple branding
and noncommercial BYD HUD images; see [third-party notices](THIRD_PARTY_NOTICES.md).
The Persona experiment makes no claim that those assets are open source.

## Building

Follow [BUILD.md](BUILD.md) for prerequisites and source build steps. The Persona
side-by-side package option is `DIPLAY_PERSONA_SIDE_BY_SIDE=1`. A successful
build or emulator launch verifies neither accessory authentication nor real USB
or wireless behavior on a car. Test only while parked and review any exported
diagnostics before sharing them publicly.
