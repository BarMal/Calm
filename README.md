# Calm

Calm is a barebones Android launcher scaffold.

## What is included

- A single Android app module.
- A Home screen launcher intent filter.
- A simple app grid built with platform Android views.
- Gradle wrapper configuration for Gradle 8.13.

## Build

Install an Android SDK, then create a `local.properties` file with your SDK path if Android Studio does not do it for you:

```properties
sdk.dir=C\:\\Users\\barna\\AppData\\Local\\Android\\Sdk
```

Then run:

```powershell
.\gradlew.bat assembleDebug
```

## Package

The application ID is `dev.barna.calm`.

