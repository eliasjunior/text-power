1. **Basic TTS Playback**
- Add `Play/Pause/Stop` using Android `TextToSpeech`.
- Read current extracted text from reader panel.

2. **Long Text Chunking**
- Split large OCR text into sentence/paragraph chunks.
- Queue chunks to avoid truncation and improve stability.

3. **Reader Playback Controls**
- Add speed slider, pitch slider, and voice/language selector.
- Persist these preferences locally.

4. **Page-Aware Narration**
- Keep multi-page headers (`Page 1`, `Page 2`) optional for speech.
- Add “start from page N” behavior.

5. **Background Playback**
- Move speech to a foreground service with media notification.
- Support screen-off playback like audiobook apps.

6. **Progress + Resume**
- Track current chunk index and char offset.
- Resume from last position after app reopen.

7. **Audio Export (Optional)**
- Add offline TTS engine export path only if needed (harder; device-dependent).
- Otherwise keep streaming device TTS playback.

8. **get all test compact the best part, like a summary, LL work, do I need a MCP ?**

If you want, I’ll implement **Step 1 + 2** first (small, high impact, minimal risk).
