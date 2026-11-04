# Home-screen backgrounds

Reference copies of the videos RavenHome plays behind the clock. They are not built into the
image: RavenHome plays whatever is in `/sdcard/Movies/RavenOS` on the device (searched
recursively), looping a single video.

| File | Source | Encoding |
|---|---|---|
| `misty-pavilion.mp4` | "The Misty Pavilion" (MoeWalls), 3840×2160 60 fps | 1422×800, 30 fps, H.264 Main, no audio, 12 s, 5.3 MB |

Re-encode a new one to fit the Echo's 1280×800 screen (RavenHome crops to fill) and push it:

```bash
ffmpeg -i in.mp4 -an -vf "scale=-2:800:flags=lanczos,fps=30" -c:v libx264 -profile:v main \
    -level 4.0 -pix_fmt yuv420p -preset slow -crf 22 -movflags +faststart out.mp4
adb push out.mp4 /sdcard/Movies/RavenOS/
```
