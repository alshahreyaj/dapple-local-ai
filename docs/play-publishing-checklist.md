# Dapple Play Store release checklist

## Project work completed

- The app targets API 36 and has a first-release version (`versionCode` 1, `versionName` 1.0).
- A privacy policy is available in the app's Settings and its source copy is in [`privacy-policy.md`](privacy-policy.md).
- Model source and license links are available under Settings → Model licenses.
- Store listing copy is drafted in [`play-store-listing.md`](play-store-listing.md).

## Still required before production

- Confirm that `com.xenevastudio.dapple` is available in Play Console before creating the app listing; this is now the configured application ID.
- The app links to the provided page, `https://xenevastudio.github.io/privacy.html`. Before release, confirm that page includes Dapple's data practices from [`privacy-policy.md`](privacy-policy.md) and add the same URL to the Play listing. Add the publisher's actual support email to the Play developer profile; the policy directs privacy questions there.
- Prepare the Google Play listing icon (512 × 512 PNG, maximum 1 MB), feature graphic (1024 × 500), and at least two screenshots.
- Create and safely store an upload key, then create a signed release Android App Bundle and enroll in Play App Signing.
- Confirm the publisher has distribution rights for the exact model files in the Hugging Face bucket, including the converted Photo Enhancer and LaMa-Dilated Object Remover files. Keep all applicable upstream attribution and license terms available to users.
- Add a trusted SHA-256 checksum for Speech Noise Remover. Its current catalog entry has no checksum and skips size verification.
- Complete the Play Console Data safety, privacy policy, ads, target audience, content rating, and app access declarations accurately, including behavior of included libraries and model downloads.
- Manually test the release candidate on supported Android phones: first install, each download and cancellation path, offline processing, unsupported files/devices, saving/sharing, orientation, memory limits, and navigation.
- If this is a personal Play developer account created after November 13, 2023, complete the required closed test before requesting production access.

Do not publish until the privacy policy is hosted and linked, model distribution rights are confirmed, and release testing is complete.
