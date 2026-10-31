# Speech-to-Text

Dapple's Speech-to-Text tool runs multilingual Whisper Base INT8 through the sherpa-onnx Android Kotlin API. The model runs locally with the sherpa CPU provider. An empty Whisper language value enables automatic language detection. The language selector follows the language codes in Whisper's multilingual tokenizer; recognition accuracy varies by language and recording quality.

## Downloaded model files

The app downloads only the runtime files below from the pinned sherpa-onnx Hugging Face model revisions. Together they are approximately 161 MB (the upstream pages display rounded file sizes). Each downloaded file is checked against its SHA-256 before installation in app-private storage.

| File | Upstream size | SHA-256 |
| --- | ---: | --- |
| `base-encoder.int8.onnx` | 29.1 MB | `0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11` |
| `base-decoder.int8.onnx` | 131 MB | `9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d` |
| `base-tokens.txt` | 817 kB | `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |

Model source: [csukuangfj/sherpa-onnx-whisper-base](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base). The ONNX files are the INT8 encoder and decoder variants exported for sherpa-onnx; the vocabulary is the multilingual Whisper token file. sherpa-onnx performs feature extraction internally from mono float audio at 16 kHz using its 80-dimension Whisper feature configuration. No tensor preprocessing is reimplemented in Dapple.

The downloaded Whisper weights derive from OpenAI Whisper, licensed MIT ([license](https://github.com/openai/whisper/blob/main/LICENSE)). The sherpa-onnx runtime is Apache-2.0 ([license](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE)). The runtime dependency follows sherpa-onnx's Android Gradle integration at version `v1.13.8`.

## Audio workflow

- Import WAV, MP3, M4A/AAC, FLAC, or OGG when Android's installed media decoder supports the selected file. Decode errors are shown as actionable messages.
- Record AAC audio in app cache after the user grants microphone access. The recording is capped at five minutes.
- Audio is downmixed and resampled to 16 kHz mono, then transcribed in sequential 30-second windows. Progress reflects completed windows; cancellation is checked between windows.
- Transcripts remain editable and can be copied, shared, or exported as TXT. Sherpa token timestamps are shown in grouped five-second intervals when available.
- The selector offers Auto-detect plus the language codes listed by Whisper's multilingual tokenizer. Selecting a language guides decoding; recognition accuracy varies by language and recording quality.
- Silence yields an empty transcript message. Audio longer than five minutes is rejected with a clear prompt to choose a shorter clip.

Model download transfers each file independently. Verified files remain available if another file's download is cancelled or fails, so retry skips valid files and fetches only the missing or invalid artifacts. Model deletion removes the installed directory and a later use downloads it again.
