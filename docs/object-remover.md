# Object Remover model integration

Dapple's Object Remover uses the Qualcomm AI Hub LaMa-Dilated TFLite float export, downloaded only after the user requests it.

- Download URL: `https://huggingface.co/buckets/xeneva-studio/localAI/resolve/object_remover.tflite?download=true`
- Model version: Qualcomm AI Hub export `0.65.0` (QAIRT 2.50)
- Approximate published model size: 174 MB. Actual byte count is read from the download response and the app does not reject the model based on an estimated length.
- SHA-256: `b7bec126dd3f3b27a6ef0e7137ca31954c1d9e8a1487d061d3cab14db023fabd`
- Reference license/source: [LaMa-Dilated model card](https://huggingface.co/qualcomm/LaMa-Dilated) and [original implementation license](https://github.com/advimman/lama/blob/main/LICENSE).

## Model contract

The Qualcomm model wrapper declares an image tensor `[1, 3, 512, 512]` and a mask tensor `[1, 1, 512, 512]`, both float32. RGB pixels are scaled to `[0,1]`; the mask is binary with `1` where the user paints and `0` elsewhere. Output is an RGB image in `[0,1]`. The LiteRT runner checks input/output names, float element types, and supported NCHW/NHWC tensor layouts at load time.

The editor keeps the source image size. It runs 512-pixel tiles with 64-pixel overlap, pads image edges by extending their nearest pixel, skips tiles with no selected pixels, and feather-blends overlapping results. Images are orientation-corrected by the shared image decoder. Oversized images and memory allocation failures return a user-facing error.

## Manual device checks

Run these on a physical Android device after downloading the model:

1. Download, cancel, retry, and confirm a wrong SHA-256 download is rejected.
2. Test square, portrait, landscape, small (under 512 px), exact 512 px, and large images.
3. Paint a central object and objects along each image edge; verify the binary mask tracks the preview, eraser, undo, and redo.
4. Compare single-tile output with overlapped multi-tile output for visible seams.
5. Verify orientation, full output dimensions, saving, reopening the exported image, and behavior under memory pressure.
6. Confirm NPU/GPU initialization falls back to CPU on devices without supported acceleration.

The workspace changes add JVM tests for tile coverage, image aspect dimensions, edge selections, and empty/transparent masks. Model output quality, accelerator availability, peak memory, and performance still need the requested real-device checks.
