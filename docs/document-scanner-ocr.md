# Document Scanner & OCR integration

This tool uses CameraX for capture, OpenCV for page-boundary detection and perspective correction, and ONNX Runtime Android for offline text detection and recognition. It keeps pages and downloaded models under app-private storage; model weights are not packaged in the APK.

## Downloaded artifacts

The catalog entry is `document-scanner-ocr`. `ModelManager` downloads each artifact into the `document-ocr-ppocrv5-latin/` directory and validates the published SHA-256 before marking the model ready.

| File | Upstream artifact | Approx. size | SHA-256 |
|---|---|---:|---|
| `det.onnx` | [PP-OCRv5 mobile detector ONNX](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx) | 4.83 MB | `a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d` |
| `rec.onnx` | [Latin PP-OCRv5 mobile recognizer ONNX](https://huggingface.co/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx) | 8.04 MB | `7888113072263cb471b93f66dd5e2ad70548dc526fa1ace760d0d973dd121498` |
| `latin_dict.txt` | [PaddleOCR PP-OCRv5 Latin dictionary](https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/utils/dict/ppocrv5_latin_dict.txt) | 2,616 bytes | `ccbcc45730b3fbbd9050c5bc74db6a99067141ef1035e3d14889a84a6b9b1aff` |

The detector and recognizer are publisher ONNX exports mirrored in Dapple's Hugging Face bucket; the catalog pins their download URLs and SHA-256 values. The combined expected file size is 12,871,157 bytes (about 12.9 MB). All three artifacts and PaddleOCR are Apache-2.0 licensed. The Android deployment guide specifies ONNX Runtime 1.21.1 and OpenCV 4.5.3, which are used here. CameraX uses stable release 1.6.2.

## Tensor and image contract

At session creation the app reads ONNX input names, shapes, and element types. It rejects non-float inputs, non-NCHW three-channel inputs, a recognizer height other than 48, and incompatible recognizer output classes. Recognition crops follow the model's dynamic width range (up to the exported 3,200-pixel profile); fixed-width exports are handled at their declared width. Output dimensions are read from the loaded model at inference time. The expected Latin CTC alphabet is 836 dictionary entries plus blank and space (838 classes).

- **Detector preprocessing:** decode as BGR; resize the long side to 960; round dimensions to 32-pixel multiples; convert to NCHW float32; scale by `1/255`; normalize with mean `[0.485, 0.456, 0.406]` and standard deviation `[0.229, 0.224, 0.225]`.
- **Detector postprocessing:** DB probability map; threshold `0.3`; box score threshold `0.6`; maximum 1,000 contours; expansion ratio 1.5. Coordinates are mapped back to the page bitmap before recognition.
- **Recognizer preprocessing:** BGR text crops resized to height 48, keeping aspect ratio, with width capped by the model's declared/dynamic width (the published profile allows up to 3,200); normalize each pixel to `[-1, 1]`; zero-pad static-width NCHW inputs as needed.
- **Recognizer postprocessing:** CTC greedy decoding, remove blank tokens and repeated adjacent tokens, then map labels through the verified Latin dictionary. The alphabet supports English and German among its Latin-script languages.

The session verifies compatible metadata on-device rather than assuming an exported shape from a model-card label. If an artifact has a different contract, initialization fails with an actionable error instead of passing an incompatible tensor.

## Workflow and limits

The camera is optional and requested only when the user taps **Use camera** or **Scan page**. Photo Picker import is available as an alternative. Image decoding uses Dapple's bounded-photo decoder; very large originals may be reduced to fit device memory. Users can adjust detected page corners, apply the perspective correction, rotate, choose a grayscale / black-and-white / contrast filter, collect and reorder multiple pages, edit OCR text, and export TXT or image-backed searchable PDF. All recognition runs locally after model download.

OCR geometry and the PDF text layer are generated from detected text regions. Recognition quality depends on focus, lighting, language, and the phone's available memory. The official detector config's score thresholds and resize settings are followed; this initial app integration uses OpenCV contour boxes and rectangular expansion for DB postprocessing rather than Paddle's Python `pyclipper` polygon-offset implementation, so unusually rotated or curved text can differ from PaddleOCR's reference pipeline. This implementation has not been device-tested here; run the manual camera, OCR, PDF text-selection, memory, and artifact-download checks on-device.
