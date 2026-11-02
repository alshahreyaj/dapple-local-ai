#!/usr/bin/env python3
"""Run the official PyTorch BOPBTL and LaMa inference scripts for image fixtures.

Requires both upstream repositories, their documented checkpoints, and each
repository's Python dependencies. This keeps the original implementations as the
reference instead of reimplementing their preprocessing here.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageOps


def run(command: list[str], cwd: Path) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bopbtl-repo", type=Path, required=True, help="Bringing-Old-Photos-Back-to-Life checkout")
    parser.add_argument("--lama-repo", type=Path, required=True, help="LaMa checkout")
    parser.add_argument("--lama-model", type=Path, required=True, help="Unpacked official LaMa checkpoint directory")
    parser.add_argument("--fixtures", type=Path, required=True, help="Directory of upright JPG/PNG/WebP photos")
    parser.add_argument("--output", type=Path, required=True, help="Directory for reference intermediates")
    parser.add_argument("--gpu", default="-1", help="BOPBTL GPU id; -1 selects CPU")
    parser.add_argument("--input-size", choices=("scale_256", "full_size"), default="scale_256")
    parser.add_argument("--max-pixels", type=int, default=2_000_000, help="Match Dapple's bounded edit input size")
    args = parser.parse_args()

    bop_repo = args.bopbtl_repo.resolve()
    lama_repo = args.lama_repo.resolve()
    fixtures = args.fixtures.resolve()
    output = args.output.resolve()
    lama_model = args.lama_model.resolve()
    bop_script = bop_repo / "Global" / "detection.py"
    lama_script = lama_repo / "bin" / "predict.py"
    bop_weights = bop_repo / "Global" / "checkpoints" / "detection" / "FT_Epoch_latest.pt"
    if not bop_script.is_file() or not bop_weights.is_file():
        raise SystemExit("BOPBTL source/checkpoint missing. Follow the upstream README to install Global checkpoints.")
    if not lama_script.is_file() or not (lama_model / "config.yaml").is_file():
        raise SystemExit("LaMa source/checkpoint missing. Supply the unpacked big-lama directory containing config.yaml.")
    if not fixtures.is_dir():
        raise SystemExit(f"Fixture directory not found: {fixtures}")

    images = sorted(p for p in fixtures.iterdir() if p.is_file() and p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"})
    if not images:
        raise SystemExit("No JPG, PNG or WebP fixtures found")

    bop_input = output / "bopbtl" / "input"
    bop_mask_dir = output / "bopbtl" / "mask"
    prepared_dir = output / "reference-input"
    lama_input = output / "lama" / "input"
    lama_output = output / "lama" / "output"
    for path in (bop_input, bop_mask_dir, prepared_dir, lama_input, lama_output):
        path.mkdir(parents=True, exist_ok=True)

    prepared_images: list[Path] = []
    for image in images:
        with Image.open(image) as original:
            rgb = ImageOps.exif_transpose(original).convert("RGB")
            scale = min(1.0, (args.max_pixels / (rgb.width * rgb.height)) ** 0.5)
            if scale < 1.0:
                rgb = rgb.resize((max(1, int(rgb.width * scale)), max(1, int(rgb.height * scale))), Image.Resampling.LANCZOS)
            prepared = prepared_dir / f"{image.stem}.png"
            rgb.save(prepared)
            prepared_images.append(prepared)
        shutil.copy2(prepared, bop_input / prepared.name)

    # This is the upstream detector script. It saves its official thresholded mask output.
    run(
        [sys.executable, str(bop_script), "--test_path", str(bop_input), "--output_dir", str(output / "bopbtl"), "--input_size", args.input_size, "--GPU", args.gpu],
        bop_repo / "Global",
    )

    manifest: list[dict[str, str]] = []
    for image_path in prepared_images:
        mask_path = bop_mask_dir / f"{image_path.stem}.png"
        if not mask_path.is_file():
            raise SystemExit(f"Official BOPBTL didn't produce expected mask: {mask_path}")
        with Image.open(image_path) as image:
            rgb = image.convert("RGB")
            rgb.save(lama_input / f"{image_path.stem}.png")
        with Image.open(mask_path) as mask:
            # Keep the upstream thresholded mask pixels; only convert file encoding/mode.
            mask = mask.convert("L")
            with Image.open(image_path) as image:
                if mask.size != image.size:
                    mask = mask.resize(image.size, Image.Resampling.NEAREST)
            mask.save(lama_input / f"{image_path.stem}_mask001.png")
            mask.save(output / f"{image_path.stem}.mask.png")
        manifest.append({"image": image_path.name, "bopbtl_mask": str(mask_path)})

    # LaMa's official dataset uses RGB [0,1], L [0,1] masks and symmetric modulo padding.
    run(
        [
            sys.executable, str(lama_script),
            f"model.path={lama_model}", f"indir={lama_input}", f"outdir={lama_output}",
            "dataset.img_suffix=.png", "dataset.pad_out_to_modulo=8", "device=cpu", "refine=False",
        ],
        lama_repo,
    )

    # Normalize output names to the instrumentation harness's golden naming convention.
    for image_path in prepared_images:
        upstream_prediction = lama_output / f"{image_path.stem}_mask001.png"
        if not upstream_prediction.is_file():
            candidates = sorted(lama_output.rglob(f"{image_path.stem}_mask001.png"))
            upstream_prediction = candidates[0] if candidates else upstream_prediction
        if not upstream_prediction.is_file():
            raise SystemExit(f"Official LaMa didn't produce expected output for {image_path.name}")
        shutil.copy2(upstream_prediction, output / f"{image_path.stem}.restored.png")

    (output / "reference-manifest.json").write_text(json.dumps({"images": manifest, "input_size": args.input_size, "max_pixels": args.max_pixels}, indent=2) + "\n")
    print(f"Reference masks and LaMa outputs saved under {output}")


if __name__ == "__main__":
    main()
