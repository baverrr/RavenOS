# RavenOS voice control: "Hey Raven"

RavenOS answers to **"Hey Raven"**, fully on the Echo: wake word, speech recognition and speech
output all run offline. It's built from three pieces:

| Piece | What it does | Where |
| --- | --- | --- |
| **Dicio** (RavenOS fork) | The assistant: listens for the wake word, understands commands, runs reminders and alarms | `apps/Dicio/` (prebuilt, privileged) |
| **Hey Raven model** | OpenWakeWord model trained for the phrase "hey raven" | `wakeword/hey_raven.tflite`, bundled inside Dicio |
| **RHVoice** | Offline text-to-speech, so Dicio can talk | `apps/RHVoice/` (F-Droid build, prebuilt) |

> [!TIP]
> After a fresh install, open **RHVoice** once and download an English voice. Until then the
> Echo shows its replies but can't speak them.

## What you can say

| "Hey Raven, …" | What happens |
| --- | --- |
| "remind me to call mom tomorrow at six pm" | Saves a reminder. When due, a full-screen card chimes and reads it aloud a few times. Asks "When should I remind you?" if no time was given. |
| "what are my reminders" | Lists them. |
| "cancel my reminder to call mom" / "cancel my reminders" | Cancels the best match, or all of them. |
| "go to sleep" / "go to sleep until six thirty" | Asks for a wake-up time if needed, then dims the screen to a faint clock at the lowest brightness. |
| "wake me up at seven" | Sets the wake-up alarm without the sleep screen. |
| "turn off" | Stops a ringing alarm or reminder. |
| "snooze" / "snooze for five minutes" | Rings again in 10 minutes (or as said). |
| "wake up" | Leaves the sleep screen and turns that alarm off. |

Plus Dicio's own skills: weather, timers, time, calculator, media, opening apps, and more.

The alarm rings in bursts with short gaps, and goes quiet for about 12 seconds after "Hey
Raven", so the microphone can hear "turn off" over it. Alarms and reminders survive reboots.

## How it fits together

```
"Hey Raven" ──▶ WakeService (OpenWakeWord, hey_raven.tflite)
                    │ opens MainActivity directly (privileged: START_ACTIVITIES_FROM_BACKGROUND)
                    ▼
              Vosk speech-to-text ──▶ skills (reminder, sleep, alert_control, …)
                                              │
                         Alerts (stored, setAlarmClock) ──▶ AlertReceiver ──▶ AlertActivity
                                                                                │ chime / alarm
                                                                                ▼
                                                                     RHVoice speaks the text
```

## Building

### Dicio

The fork is upstream Dicio at a pinned commit plus `apps/Dicio/patches/`. Its working copy is
`/mnt/ssd/ravenos-dicio` (branch `ravenos`).

```bash
bash vendor/ravenos/apps/Dicio/build-dicio.sh            # build, refresh Dicio.apk and Android.mk
bash vendor/ravenos/apps/Dicio/build-dicio.sh --export   # also save the fork's commits as patches
```

It builds in Docker (`apps/Dicio/build/Dockerfile`: JDK 21 and 17, Android SDK 36), which
accepts the Android SDK License Agreement.

### The "Hey Raven" model

```bash
bash vendor/ravenos/tools/wakeword/train.sh            # all steps
bash vendor/ravenos/tools/wakeword/train.sh train      # resume from a step: image, data, generate, augment, train, convert
```

It downloads about 20 GB of training data to `/mnt/ssd/ravenos-wakeword` and trains on the CPU
in Docker, taking a few hours. Settings are in `tools/wakeword/hey_raven.yml`, including the
look-alike phrases ("hey ray", "heaven", "hey dicio", …) it learns to ignore. The result lands in
`wakeword/hey_raven.tflite`; copy it into the fork's `app/src/main/assets/openWakeWord/` and
rebuild Dicio.

The `convert` step turns the trained ONNX model into TFLite in a separate process, because
openWakeWord's own conversion aborts when torch and TensorFlow are loaded together.

Three runs were scored on the same held-out data: 1,300 noisy "hey raven" clips and 10.7 hours
of ordinary speech and noise. "Caught" is the share of clips detected; "false wakes" counts
separate activations per hour of the non-wake audio.

| Run | Settings | Threshold | Caught | False wakes / hour |
| --- | --- | --- | --- | --- |
| 1 | layer 32, 50k steps, negative weight 1500 | 0.8 | 42% | 0.19 |
| 2 | layer 64, 80k steps, negative weight 600 | 0.95 | 59% | 2.1 |
| **3 (shipped)** | layer 64, 80k steps, negative weight 1100 | **0.98** | **50%** | **0.28** |

Dicio's threshold is `WAKE_THRESHOLD` in the fork's `OpenWakeWordDevice.kt`. The test clips are
noisier than a quiet room, so real-world detection should be better than "Caught" suggests.
Next steps for more detection: `augmentation_rounds: 3`, more generated clips, or recordings of
real voices on the Echo's microphones.

## Microphone source

Dicio records the wake word and speech from `VOICE_COMMUNICATION`, Amazon's voice pipeline
with gain, noise reduction and echo cancellation. Its default `VOICE_RECOGNITION` pipeline
beamforms but applies no gain, so speech arrived about 20 dB quieter and the wake word never
fired. Measured with speech at the usual distance:

| Source | Loudest second | Best wake score |
| --- | --- | --- |
| `VOICE_RECOGNITION` | -40.7 dB | 0.991 |
| **`VOICE_COMMUNICATION`** | **-21.4 dB** | **0.998** |
| `UNPROCESSED` | -32.3 dB | 0.997 |
| `DEFAULT` | -65.2 dB | 0.0007 |

The source is `MIC_SOURCE` in the fork's `io/audio/MicSource.kt`. Vosk's own `SpeechService`
hardcodes `VOICE_RECOGNITION`, so the fork uses `MicSpeechService`, a copy that records from
`MIC_SOURCE`. The EchoParts *Microphone processing* setting only changes plain mic and camera
recordings, not these.

## Known limits

- **English only** for reminders, sleep and turn-off.
- **First use downloads Dicio's speech recognition model** (Vosk, about 40 MB) once.
- **Voices are downloaded in RHVoice** once, after the first install.
- **Licenses:** the openWakeWord feature models and the ACAV100M training features are licensed
  for non-commercial use, so the Hey Raven model is for personal use. Dicio is GPL-3.0; the
  fork's changes are in `apps/Dicio/patches/`.
