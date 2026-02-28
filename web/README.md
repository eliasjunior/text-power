# Snap → OCR → Speak (EN/NL)

A tiny browser-only experiment that:
1) lets you capture/upload an image with text,
2) extracts the text (OCR),
3) reads the extracted text aloud.

No backend required.

## How it works

### 1) Image input
- Uses a standard `<input type="file" accept="image/*" capture="environment">`.
- On mobile (especially Chrome on Android), this usually offers **Camera** as an option.
- The selected image is previewed with `URL.createObjectURL(file)`.

### 2) OCR (Image → Text)
- Uses **Tesseract.js** in the browser.
- Language is controlled via a select box:
  - `eng` for English
  - `nld` for Dutch
- When you click **OCR**, it runs recognition and prints the result into a `<textarea>`.

### 3) Text-to-Speech (Text → Audio)
- Uses the browser's built-in **Web Speech API** (`speechSynthesis`).
- Language select box controls speech language:
  - English → `en-US`
  - Dutch → `nl-NL`
- A voice dropdown lists available voices on the device/browser.
  - The app tries to auto-pick a reasonable default voice based on language.
  - You can manually override by selecting another voice.
- Click **Speak** to read aloud.
- Click **Stop** to cancel speech immediately.

## Run it

Option 1:
- Open `index.html` directly in a browser.

Option 2 (recommended for phone testing on home network):
1. Start the local Node server:
   - `npm start`
2. In the terminal output, look for a `LAN: http://...:8080` URL.
3. Open that URL on your phone (must be on the same Wi-Fi network).

Notes:
- The server listens on `0.0.0.0:8080` by default (all network interfaces).
- You can change the port with `PORT=3000 npm start`.
- If phone access fails, allow incoming connections for Terminal/Node in your OS firewall.

## Known limitations

- OCR accuracy depends heavily on image quality (lighting, blur, font, angle).
- Web Speech voices vary across browsers/OS (availability and quality differs).
- Dutch voices are typically best on Chrome/Android and macOS; can be limited on some setups.

## Next easy upgrades

- Add image preprocessing (contrast/threshold) before OCR for better accuracy.
- Add a “Copy text” button.
- Auto-pick OCR language based on detection or a quick heuristic.
