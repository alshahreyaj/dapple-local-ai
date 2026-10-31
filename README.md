# Dapple

Dapple brings practical AI tools for photos and audio to your phone. Background Remover, Photo Enhancer, Object Remover, Speech Noise Remover, and Speech-to-Text are available now; choose a tool to download it, then work with your files on your phone. Your photos and recordings aren't uploaded for processing. More tools are in development.

## Build

- Android Studio with JDK 17 and Android SDK 36
- Gradle 9.5.0 via the included wrapper (AGP 8.13.2's documented minimum is Gradle 8.13)

Open this directory in Android Studio and run the `app` configuration, or build with `./gradlew :app:assembleDebug`. The model is downloaded on first use and is not bundled in the APK.

## Play Store release

The current project configuration uses application ID `com.xenevastudio.dapple`, version name `1.1` (`versionCode` 2), and target API 36. Before preparing another release, check the latest version code in Play Console and increase this project's `versionCode` beyond it. Keep the application ID and the app's existing Play signing setup unchanged. See the [Play Store release checklist](docs/play-publishing-checklist.md).

## Model and reference implementation

- Model: [litert-community/ormbg-LiteRT](https://huggingface.co/litert-community/ormbg-LiteRT)
- Integration contract: [LiteRT-Models ORMBG integration](https://github.com/john-rocky/LiteRT-Models/blob/main/ormbg/INTEGRATION.md)
- License: Apache-2.0

Speech Noise Remover uses Ceva's DPDFNet8 48 kHz HR TFLite model. It accepts 16-bit PCM WAV and device-decodable MP3, M4A/AAC, FLAC, and OGG/Opus files, converts audio to 48 kHz mono, performs frame-wise on-device TFLite inference, and writes an enhanced 48 kHz WAV. Compressed audio is decoded with Android MediaExtractor and MediaCodec; available codecs depend on the device. The model downloads on demand from the Dapple Hugging Face bucket.

- Original model: [Ceva-IP/DPDFNet](https://huggingface.co/Ceva-IP/DPDFNet), artifact `dpdfnet8_48khz_hr.tflite`
- Reference TFLite preprocessing and inference: [Ceva-IP/DPDFNet TFLite inference script](https://github.com/ceva-ip/DPDFNet/blob/main/tflite_model/infer_dpdfnet_tflite.py)
- License: Apache-2.0

Object Remover uses the Qualcomm AI Hub LaMa-Dilated TFLite float export. The input is a 512×512 RGB float image and a binary one-channel mask; pixels use `[0,1]` values. Dapple uses overlapping tiles for larger images, feather-blends their outputs, and tries NPU, GPU, then CPU execution.

- Model source and contract: [Qualcomm LaMa-Dilated](https://huggingface.co/qualcomm/LaMa-Dilated)
- Downloaded artifact: `object_remover.tflite` from the Dapple Hugging Face bucket
- SHA-256 verified on download: `b7bec126dd3f3b27a6ef0e7137ca31954c1d9e8a1487d061d3cab14db023fabd`
- Details and manual device test cases: [`docs/object-remover.md`](docs/object-remover.md)

`BgRemover.kt` follows the reference's 1024×1024 stretch, RGB NCHW `[0,1]` input and per-image min-max output normalization. The model requires GPU support; initialization errors are surfaced to the user and there is no CPU fallback.

The upstream DPDFNet8 48 kHz HR TFLite artifact is listed at about 18.7 MB. The bucket copy's exact checksum has not been recorded, so download installation checks the downloaded TFLite tensor contract when the model is opened rather than claiming artifact checksum verification.

Speech-to-Text uses multilingual Whisper Base INT8 exported for sherpa-onnx. It downloads the ONNX encoder, ONNX decoder, and multilingual token file separately (about 161 MB combined), verifies each file with SHA-256, and stores them in app-private storage. sherpa-onnx receives mono float audio at 16 kHz, computes the required 80-bin features, and runs CPU inference. Automatic language detection is selected by default; the language selector follows Whisper's multilingual tokenizer list. Recognition accuracy varies by language and recording quality. Inference is split into 30-second sections to keep memory use bounded and allow cancellation between sections. Imported audio decoding depends on Android's codecs and recordings are currently limited to five minutes.

- Model files: [sherpa-onnx Whisper Base](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base)
- Runtime: [sherpa-onnx Android integration](https://github.com/k2-fsa/sherpa-onnx/tree/master/android)
- Whisper model license: MIT ([OpenAI Whisper license](https://github.com/openai/whisper/blob/main/LICENSE)); sherpa-onnx runtime license: Apache-2.0
- Detailed file checksums and audio workflow: [`docs/speech-to-text.md`](docs/speech-to-text.md)
