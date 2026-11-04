#!/usr/bin/env bash
# Turns any video into a RavenOS background: 1280x720 H.264 (the Echo Show 8's
# decoder tops out at 1080p and its screen is 1280x800), no audio, SDR colour,
# a keyframe every 2 s so the loop restarts cleanly.
#
#   make-background-video.sh <input> <output.mp4> [start-seconds] [end-seconds]
#
# HDR10 / Dolby Vision sources are tone-mapped to SDR (the panel isn't HDR).
# Copy the result to the device's /sdcard/Movies/RavenOS/.
set -euo pipefail
in="$1"; out="$2"; start="${3:-0}"; end="${4:-}"

transfer=$(ffprobe -v error -select_streams v:0 -show_entries stream=color_transfer \
    -of default=nw=1:nk=1 "$in")
if [ "$transfer" = smpte2084 ] || [ "$transfer" = arib-std-b67 ]; then
    vf="zscale=w=1280:h=720:t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,"
    vf+="tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p"
else
    vf="scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:-1:-1,format=yuv420p"
fi

range=(-ss "$start")
[ -n "$end" ] && range+=(-to "$end")

ffmpeg -nostdin -hide_banner -stats "${range[@]}" -i "$in" -map 0:v:0 -an -sn -dn \
    -vf "$vf" -c:v libx264 -preset slow -tune film -crf 21 \
    -maxrate 3M -bufsize 6M -profile:v high -level:v 4.0 \
    -g 48 -keyint_min 48 -sc_threshold 0 \
    -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
    -movflags +faststart -y "$out"
