# Tool preview assets

Place tool previews in `app/src/main/res/drawable-nodpi/` as optimized WebP files. Android drawable names must use lowercase letters, numbers, and underscores.

Recommended export: 640 × 480 px (4:3), around 80–85% quality, ideally under 100 KB. The Background Remover preview currently uses `bg_remover_1.webp`.

Register an asset on its `AiFeature` with `previewResource = R.drawable.asset_name`. Features without a preview resource continue to use the generated illustration.
