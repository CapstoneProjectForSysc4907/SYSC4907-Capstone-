# Driving Environment Emulator for Autonomous Vehicle Testing

This project is a Java-based driving simulator
The simulator loads road data from Google Maps and 
shows a Street View style driving. It lets the user drive using the keyboard.


## Controls

- **W**: move forward
- **S**: brake
- **A**: steer left
- **D**: steer right
- **Esc**: exit the simulator

## Requirements

Before running the project, make sure you have:

- Java JDK 17 or newer
- Internet connection
- A Google Maps API key

## Google APIs Requirements

The source code needs these Google services:

- Roads API
- Street View Static API
- Maps Static API

Enable them in your Google Cloud project and use the API key through an **environment variable** named:

`MapsAPIKey`

## Setup

### 1. Get the source code

Clone the repo or unzip the project files.


### 2. Add the required libraries

IntelliJ:

- Create or open your Java project
- Add the source files under `src/main/java`
- Add the required dependencies through Maven, Gradle, or the IDE project settings

If you are using Maven, add dependencies for:

- `org.jmonkeyengine:jme3-core`
- `org.jmonkeyengine:jme3-desktop`
- Bullet / Minie support
- `com.github.kwhat:jnativehook`
- `com.squareup.okhttp3:okhttp`
- `com.google.code.gson:gson`
- `com.opencsv:opencsv`

### 3. Make sure you have an Environment variable containing your Google Maps API key

## Run the Project

### Option 1: Run from IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Make sure all dependencies are added
3. Make sure `MapsAPIKey` is set in your system
4. Run:

```text
group7.capstone.Main
```

### Option 2: Run with Maven

Use this only if your project includes a working `pom.xml`

```cmd
mvn clean compile
mvn exec:java -Dexec.mainClass="group7.capstone.Main"
```



## Change Location

The current hardcoded starting position in `Main.java` is:

- Latitude: `45.4191133`
- Longitude: `-75.6995299`
- Heading: `170`

The new starting position can be changed by changing these values.
