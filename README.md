# ScribbleTablet Android

Android version of ScribbleTablet - a drawing canvas app with AI-powered magic ink generation.

## Features

- **Drawing Canvas**: Free-form drawing with touch and stylus support
- **Permanent Ink**: Regular black ink for standard drawing
- **Magic Ink**: Green ink that triggers AI generation when you tap Play
- **AI Generation**: Sends your drawings to the backend API to generate images, text, and web content
- **Cards**: Generated content appears as draggable cards on the canvas

## Architecture

- **Jetpack Compose**: Modern declarative UI
- **Kotlin Coroutines**: Async operations
- **Ktor**: HTTP client for API calls
- **Coil**: Image loading

## Backend API

Uses the same backend as the iOS version:
- Endpoint: `https://scribble-backend-production.up.railway.app/v1/executeMagicInk`
- Sends canvas snapshot as base64 PNG
- Receives actions to place images, text, or web content

## Building

1. Open in Android Studio
2. Sync Gradle files
3. Run on device or emulator (API 26+)

```bash
./gradlew assembleDebug
```

## Project Structure

```
app/src/main/java/com/commonknowledge/scribbletablet/
├── data/
│   ├── model/          # Data models (API requests/responses, canvas models)
│   └── service/        # GenerationService for API calls
├── ui/
│   ├── canvas/         # DrawingCanvas composable
│   ├── toolbar/        # CanvasToolbar composable
│   ├── cards/          # Card view composables
│   └── theme/          # Material theme
├── viewmodel/          # CanvasViewModel
└── MainActivity.kt
```
