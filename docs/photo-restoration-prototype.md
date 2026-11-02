# Photo restoration PTE research prototype

This report records what is verified for the Yesteryear/keepsake PTE exports and what remains unknown. The app now runs an experimental BOPBTL → LaMa → DDColor → Real-ESRGAN General x4v3 pipeline. Colorization is a required stage of every restoration. Models are loaded one stage at a time. The BOPBTL and LaMa choices below remain experimental where the publisher doesn't document export-specific behavior.

## Sources and pinned artifacts

Primary source: [JoshuaPeddle/keepsake-models](https://huggingface.co/JoshuaPeddle/keepsake-models), pinned to repository revision `ecdb76b5852ced5fc1d65a9ba5d916d407f6382b`. Its card describes the files as ExecuTorch 1.4 XNNPACK exports. The sizes and SHA-256 values below were checked against that revision's LFS metadata and downloaded files.

| Stage | Artifact | Bytes | SHA-256 | Upstream/license |
|---|---|---:|---|---|
| Scratch detector | `bopbtl_scratch_t384_xnnpack_int8.pte` | 37,772,036 | `651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5` | [Bringing-Old-Photos-Back-to-Life](https://github.com/microsoft/Bringing-Old-Photos-Back-to-Life), MIT |
| Inpainting | `lama_big_t512_xnnpack.pte` | 204,361,100 | `5a808536fcbe0624bb81f0bb8ad20fca051efd640e994a720a78d066204947f4` | [LaMa](https://github.com/advimman/lama), Apache-2.0 |
| Colorization | `ddcolor_paper_tiny_t512_xnnpack.pte` | 242,297,736 | `a582fb6ae0d65c2c6424efcd53a8da38a3c38c5a22f7288b48aa29abbcfe6c89` | [DDColor](https://github.com/piddnad/DDColor), Apache-2.0 |

The three PTEs total 484,430,872 bytes (about 484 MB / 462 MiB). Photo Restoration adds a dedicated 3,549,456-byte Real-ESRGAN General x4v3 LiteRT model, making its full model set 487,980,328 bytes (about 488 MB / 465 MiB). The standalone Photo Enhancer continues to use its separate model. The keepsake repository's license notice also calls out dataset/license terms; review that notice and each upstream license before distributing the combined feature. The PTEs are third-party converted exports with wrapper/operator changes described by their publisher, not artifacts published by the upstream research repositories.

## PTE tensor metadata

Tensor contracts below come from parsing the PTE program/method metadata, not from inference guesses. All three expose one `forward` method, one tensor input and one tensor output, and lower to the XNNPACK delegate. Input/output scalar type is FP32; `dim_order = [0,1,2,3]`, i.e. NCHW.

| PTE | Input | Output | Verified from PTE | Not established by PTE metadata |
|---|---|---|---|---|
| BOPBTL scratch | `[1,3,384,384]` FP32 NCHW | `[1,1,384,384]` FP32 NCHW | Static shapes; XNNPACK delegate; INT8 model weights are described by publisher | RGB vs grayscale semantics, normalization, whether sigmoid is included, output score range, threshold and mask polarity |
| LaMa Big | `[1,4,512,512]` FP32 NCHW | `[1,3,512,512]` FP32 NCHW | Static shapes; XNNPACK delegate. Keepsake card says app wrapper takes RGB + mask and composites its output. | Numeric RGB range, mask polarity, output range, padding, tile placement/stride/overlap/blending |
| DDColor Tiny | `[1,3,512,512]` FP32 NCHW | `[1,2,512,512]` FP32 NCHW | Static shapes; XNNPACK delegate; upstream notebook maps output to Lab a/b channels | Equivalence of Kotlin Lab conversion and exact PTE wrapper behavior still needs golden validation |

The BOPBTL file name and card identify its 384 input and quantized weights. LaMa and DDColor filenames/card identify 512 input. The PTE shape inspection establishes static model tensor dimensions; it does not establish that an input photo should be distorted to a square or that the model has an internal resize.

### Photo Restoration detail enhancer

The LiteRT Community [Real-ESRGAN General x4v3 export](https://huggingface.co/litert-community/real-esrgan-x4v3-litert/tree/0a6fc35ba3e34e80b53f43089c1d2b612d33bea9) is a separate `.tflite` artifact (3,549,456 bytes; SHA-256 `6d597b46d812ada42bdde641ec6e78c0ef0b78ff5a825fcfd74b9a1b3bcbe9a4`), licensed BSD-3-Clause. Its published contract is RGB float `[1,128,128,3]` NHWC in `[0,1]` to RGB float `[1,3,512,512]` NCHW in `[0,1]`. The app tiles the repaired, colorized image into 128×128 patches, clamps at image edges and blends with 32px overlap. LiteRT tries Qualcomm NPU on Qualcomm devices, then GPU, then CPU. The standalone Photo Enhancer model is not used or changed by this stage.

## Comparison with upstream pipelines

### BOPBTL scratch detector

The official upstream [`Global/detection.py`](https://github.com/microsoft/Bringing-Old-Photos-Back-to-Life/blob/master/Global/detection.py) converts an image to RGB then grayscale, scales/rounds dimensions in multiples of 16, maps grayscale tensor values through mean/std `0.5` (the resulting `[-1,1]` convention), uses bilinear scaling, applies sigmoid, resizes the score map with nearest interpolation and thresholds at `>= 0.4`. This is the original PyTorch inference path. The converted PTE takes **three** channels at 384 square, so those external transforms cannot be copied over as a presumed PTE contract. The exporter may have embedded different conversions or the exported wrapper may expect a different representation.

### LaMa

The official [`bin/predict.py`](https://github.com/advimman/lama/blob/main/bin/predict.py) obtains image and mask tensors from its configured dataset, binarizes mask values with `mask > 0`, executes the model, crops to `unpad_to_size` when present, converts the result from RGB to BGR for file output, and maps normalized values to bytes by multiplying by 255 and clipping. The keepsake card additionally says its app wrapper accepts RGB plus a mask and composites the output. That verifies the wrapper's broad purpose, not the numeric range, mask polarity, or tile strategy. Its fixed 512 tile statement establishes tile dimensions, but not stride, overlap, edge handling, or blending.

### DDColor

The official [DDColor ONNX inference notebook](https://github.com/piddnad/DDColor/blob/master/demo/colorization_pipeline_onnxruntime.ipynb) provides a concrete upstream path: BGR byte image to float `[0,1]`, retain original Lab L, resize to 512 square, make a neutral Lab image from L and zero a/b, convert it to RGB, and feed FP32 NCHW. It resizes the two output channels to the original size using PyTorch's default nearest interpolation, concatenates them with original L, converts Lab to RGB and scales to bytes. `DdcolorColorizer` follows that path with an in-app CIELAB conversion. The PTE's two output channels and static shape are verified; byte-for-byte equivalence of the Kotlin color conversion to OpenCV still needs a golden-image comparison.

## Prototype tests

`app/src/androidTest/.../RestorationPteSmokeTest.kt` tests loading and execution for BOPBTL, LaMa and DDColor with the ExecuTorch 1.4.0 Maven Central runtime. Each PTE test verifies its checksum, sends an all-zero tensor of the declared shape, and checks output count, element count and finite values. It also runs DDColor on a generated grayscale image and checks output dimensions. The BOPBTL smoke test writes a min/max-normalized **diagnostic-only** PNG of the raw output. It is not a binary mask. These tests still require execution on a device with the PTEs available.

`ModelSpecs.restorationScratch`, `ModelSpecs.restorationLama`, `ModelSpecs.restorationColorizer` and `ModelSpecs.restorationEnhancer` retain the verified byte sizes and SHA-256 hashes for their source artifacts, while their app download URLs point to `xeneva-studio/localAI/photo-restoration`. Photo Restoration downloads its dedicated enhancer on demand; the standalone Photo Enhancer model remains independent. The complete set is about 488 MB. Files stay in private app storage and are not APK assets.

`RestorationTileExperiment` generates 512px tile positions for caller-selected overlap values and provides a cosine-feather candidate. The requested 64px overlap (448px stride) is the starting experiment; the device harness compares 32px and 96px too. Partial edge tiles use reflected image/mask samples. These are not publisher rules and do not prove seam quality.

### Current prototype guesses

`PhotoRestorationPipeline` uses these replaceable defaults:

- BOPBTL input: RGB, channel-first, values `[0,1]`, nearest-neighbor resize stretched to `384×384`.
- BOPBTL output: treat an output entirely in `[0,1]` as probability; otherwise apply sigmoid. Resize the raw map to photo size with nearest-neighbor sampling and threshold at `0.4` (the original Python script uses sigmoid and `>=0.4`). This differs from the upstream grayscale/`[-1,1]` preprocessing, so this must be compared with the PTE wrapper before trusting its mask.
- LaMa input: channel-first RGB plus mask, both `[0,1]`, with damaged pixels set to 1. LaMa output is treated as RGB `[0,1]` based on the wrapper's compositing description.
- LaMa tiles: 512px tiles, 64px overlap / 448px nominal stride, reflected edge samples, cosine feather blend. Other overlap candidates are available in the experiment harness. Final compositing copies original source pixels exactly outside the predicted binary mask.
- DDColor: always follows the upstream Lab preparation and reconstruction described above. The implementation's CIELAB math has not yet been numerically compared with OpenCV's conversion.
- Detail enhancement: always runs the dedicated Real-ESRGAN General x4v3 LiteRT model after repair and colorization. It produces a 4× output with 128px tiles and 32px overlap, using LiteRT NPU/GPU/CPU selection. The tile overlap and blend behavior are app choices; the model publisher specifies 128px input tiles but does not prescribe overlap or blending.

The UI labels the results experimental. These choices are not claims about Yesteryear's private app implementation.

`tools/photo-restoration/run_upstream_reference.py` invokes the official BOPBTL `Global/detection.py` and LaMa `bin/predict.py` scripts with their original checkpoints. It saves upright, bounded sample inputs, BOPBTL masks and full-image LaMa reference outputs. `PhotoRestorationExperimentTest` accepts that prepared input directory and optional golden-output directory. It saves PTE raw scratch maps, binary masks and tiled repaired images for 32/64/96px overlap candidates, then records elapsed time, a rough heap delta, mask IoU and image PSNR when goldens exist. On-device memory deltas are not peak-RAM measurements.

Example reference command (after installing the dependencies/checkpoints documented by each official repository):

```bash
python3 tools/photo-restoration/run_upstream_reference.py \
  --bopbtl-repo /path/to/Bringing-Old-Photos-Back-to-Life \
  --lama-repo /path/to/lama \
  --lama-model /path/to/big-lama \
  --fixtures /path/to/official-or-test-photos \
  --output /tmp/dapple-restoration-reference
```

The script's `reference-input/` directory is the matching fixture directory for the Android experiment. Copy the BOPBTL, LaMa and DDColor PTEs plus `realesr_general_x4v3.tflite` and fixture/reference PNGs to device-readable storage, then set instrumentation arguments `restorationModelsDir`, `restorationFixturesDir`, `restorationGoldenDir`, and optionally `restorationOverlaps=32,64,96` when running `PhotoRestorationExperimentTest` from Android Studio. Portrait, landscape and scratched sample images should all be present in the fixture directory.

The test harness deliberately doesn't include models in either APK. To run it on a device, make the model files available in a directory readable by the instrumentation process and pass its path as the `restorationModelsDir` instrumentation argument. The repo does not include the model weights. Zero-input execution is not semantic validation and does not replace golden comparisons.

## Required before production inference

Online searches found no exporter scripts beyond the public model card. The following validations remain open:

1. **Pre/postprocessing contract**: publisher source for each exact export, including RGB/BGR/grayscale handling, tensor normalization, and whether transforms are embedded in the graph.
2. **Scratch mask visualization**: a real upstream input/output pair for the exported detector, or a documented output activation and threshold/polarity. The upstream `0.4` threshold is not assumed to apply to this wrapper.
3. **LaMa tiling**: exporter/runtime implementation or authoritative tile plan. Current candidates need seam/quality evaluation.
4. **DDColor output validation**: compare the in-app Lab conversion and result against upstream OpenCV/PyTorch outputs on licensed grayscale fixtures.
5. **Detail stage and golden comparisons**: compare final output with and without 4× enhancement, and compare LaMa and colorization stages against pinned upstream checkpoints/configs and licensed fixtures. Tensor-shape smoke tests cannot stand in for image comparisons.

The keepsake card says the model weights are unchanged and the converted operations were verified against the upstream PyTorch models. It does not include the reproducible test inputs/reference outputs or numeric transforms needed to repeat those checks here. The experiment harness can compare with golden PNGs once they are provided; candidate results have not been evaluated in this environment.

## Device/reference validation status

No Galaxy S25 Ultra is attached to this workspace. ADB could start when run outside the sandbox, but `adb devices -l` returned no attached devices, so no Android PTE execution or Galaxy runtime/RAM measurement was performed here. The instrumentation tests are ready for a connected device and require PTEs, fixtures and optionally goldens at the paths passed through instrumentation arguments. The official upstream checkpoints and expected outputs are not present locally. The smoke and geometry tests are not substitutes for those validations.
