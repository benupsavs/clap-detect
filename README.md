# Clap Detect

A Java desktop application that integrates with ffmpeg in order to detect 4 claps in a row, for synchronization purposes.

This is useful for coordinating musical performances for compositing movies into a single production.

# Requirements
- Java 17 or later
- ffmpeg

## Usage

- Launch the Java app. Install Maven and run `mvn package exec:java`
- Load the source movie
- Load the target movie
- Detect the clap position automatically
- Fine tune the clap marker if necessary
- Once the two movies sound synchronized, cut and save the target movie

Now, the target is in sync with the source.
