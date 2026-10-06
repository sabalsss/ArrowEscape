"""
Arrow Escape - SFX synthesis (Phase 6C).

Fully original audio generated from scratch with the Python standard library
only: no samples, no third-party sound packs, no copyrighted material. Every
waveform below is additive/subtractive synthesis of sine partials and
pseudo-random noise, so the output is royalty-free and safe to ship.

Writes 44.1 kHz mono 16-bit WAVs next to this script. To regenerate the
shipped assets:

    python3 tools/sfx/gen_sfx.py
    for f in sfx_escape sfx_blocked sfx_level_complete sfx_game_over sfx_shape_confirm; do
        oggenc -Q --quality 6 -o "app/src/main/res/raw/$f.ogg" "tools/sfx/$f.wav"
    done
"""
import array
import math
import os
import random
import wave

SR = 44100


# ---------- helpers -------------------------------------------------------

def silence(seconds):
    return [0.0] * int(SR * seconds)


def biquad(buf, kind, f0, q):
    """Static RBJ biquad. kind in {'lp', 'hp', 'bp'}."""
    w0 = 2.0 * math.pi * f0 / SR
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * q)
    if kind == 'lp':
        b0, b1, b2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2
    elif kind == 'hp':
        b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
    else:
        b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    x1 = x2 = y1 = y2 = 0.0
    out = [0.0] * len(buf)
    for i, x0 in enumerate(buf):
        y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        out[i] = y0
        x2, x1 = x1, x0
        y2, y1 = y1, y0
    return out


def swept_bandpass(buf, f_start, f_end, q):
    """Bandpass whose centre frequency glides f_start -> f_end (coeffs per sample)."""
    n = len(buf)
    x1 = x2 = y1 = y2 = 0.0
    out = [0.0] * n
    for i, x0 in enumerate(buf):
        frac = i / max(1, n - 1)
        f0 = f_start * (f_end / f_start) ** frac          # log sweep
        w0 = 2.0 * math.pi * f0 / SR
        cw, sw = math.cos(w0), math.sin(w0)
        alpha = sw / (2.0 * q)
        a0 = 1 + alpha
        b0, b2 = alpha / a0, -alpha / a0
        a1, a2 = (-2 * cw) / a0, (1 - alpha) / a0
        y0 = b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2
        out[i] = y0
        x2, x1 = x1, x0
        y2, y1 = y1, y0
    return out


def add(dst, src, at_seconds):
    off = int(SR * at_seconds)
    need = off + len(src)
    if need > len(dst):
        dst.extend([0.0] * (need - len(dst)))
    for i, v in enumerate(src):
        dst[off + i] += v


def partial_note(freq, dur, partials, tau, detune=0.0):
    """Additive sine stack with per-partial exponential decay."""
    n = int(SR * dur)
    out = [0.0] * n
    for mult, amp, tau_scale in partials:
        f = freq * mult * (1.0 + detune)
        w = 2.0 * math.pi * f / SR
        t_decay = tau * tau_scale
        for i in range(n):
            out[i] += amp * math.exp(-(i / SR) / t_decay) * math.sin(w * i)
    # 4 ms raised-cosine attack so the onset is soft rather than a click
    atk = int(SR * 0.004)
    for i in range(min(atk, n)):
        out[i] *= 0.5 - 0.5 * math.cos(math.pi * i / atk)
    return out


def edge_fade(buf, ms=6.0):
    """Guarantee the buffer starts and ends at zero (no DC click in SoundPool)."""
    k = int(SR * ms / 1000.0)
    for i in range(min(k, len(buf))):
        g = 0.5 - 0.5 * math.cos(math.pi * i / k)
        buf[i] *= g
        buf[-1 - i] *= g
    return buf


def write_wav(path, buf, peak):
    """Normalise to `peak` (per-sound loudness balance) and write 16-bit mono."""
    m = max(1e-9, max(abs(v) for v in buf))
    g = peak / m
    data = array.array('h', (max(-32768, min(32767, int(v * g * 32767.0))) for v in buf))
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    return len(buf) / SR


# ---------- sfx_escape : soft upward whoosh, ~190 ms ----------------------

def escape():
    rnd = random.Random(0xE5C4)
    dur = 0.19
    n = int(SR * dur)
    noise = [rnd.uniform(-1.0, 1.0) for _ in range(n)]
    # two swept resonant bands = airy "whoosh" rather than a hiss
    band = swept_bandpass(noise, 850.0, 3000.0, 1.1)
    band2 = swept_bandpass(noise, 1700.0, 5200.0, 2.4)
    body = []
    for i in range(n):
        t = i / SR
        f = 420.0 + 300.0 * t / dur                       # gentle pitch rise
        body.append(math.sin(2.0 * math.pi * f * t))
    buf = [0.80 * band[i] + 0.30 * band2[i] + 0.22 * body[i] for i in range(n)]
    buf = biquad(buf, 'hp', 300.0, 0.707)                 # drop rumble a phone cannot play
    buf = biquad(buf, 'lp', 6200.0, 0.707)                # take the edge off
    for i in range(n):
        t = i / SR
        env = (1.0 - math.exp(-t / 0.0045)) * math.exp(-t / 0.062)
        buf[i] *= env
    # Gentle saturation: lifts RMS (perceived loudness) without lifting the
    # peak, so the whoosh carries over a phone speaker instead of vanishing.
    m = max(abs(v) for v in buf) or 1.0
    buf = [math.tanh(2.2 * v / m) for v in buf]
    return edge_fade(buf, 8.0), dur


# ---------- sfx_blocked : soft muted knock, ~140 ms ----------------------

def blocked():
    rnd = random.Random(0xB10C)
    dur = 0.14
    n = int(SR * dur)
    buf = [0.0] * n
    for i in range(n):
        t = i / SR
        # thump with a short downward pitch droop, like a padded tap
        f = 185.0 * math.exp(-t / 0.045) + 118.0
        phase = 2.0 * math.pi * (118.0 * t + 185.0 * 0.045 * (1 - math.exp(-t / 0.045)))
        buf[i] = 0.50 * math.sin(phase) * math.exp(-t / 0.034)
        # Harmonic stack. A phone speaker reproduces almost nothing below
        # ~500 Hz, so the knock is carried by its upper partials and the ear
        # restores the missing fundamental. Same knock, now actually audible.
        buf[i] += 0.90 * math.sin(2.02 * phase) * math.exp(-t / 0.026)
        buf[i] += 0.62 * math.sin(3.01 * phase) * math.exp(-t / 0.020)
        buf[i] += 0.32 * math.sin(4.03 * phase) * math.exp(-t / 0.014)
    # tiny mallet-contact transient
    tick_n = int(SR * 0.012)
    tick = [rnd.uniform(-1.0, 1.0) for _ in range(tick_n)]
    tick = biquad(tick, 'lp', 2600.0, 0.8)
    for i in range(tick_n):
        buf[i] += 0.55 * tick[i] * math.exp(-(i / SR) / 0.005)
    buf = biquad(buf, 'lp', 4200.0, 0.707)                # muted, never clacky
    buf = biquad(buf, 'hp', 210.0, 0.707)                 # bin the unplayable sub
    return edge_fade(buf, 6.0), dur


# ---------- sfx_level_complete : ascending chime, ~740 ms ----------------

def level_complete():
    # C6 - E6 - G6 - C7 (major pentatonic arpeggio, unambiguously positive)
    notes = [(1046.50, 0.00, 0.26), (1318.51, 0.095, 0.26),
             (1567.98, 0.190, 0.28), (2093.00, 0.300, 0.44)]
    bell = [(1.0, 1.00, 1.00), (2.0, 0.26, 0.62),
            (3.0, 0.11, 0.42), (4.01, 0.045, 0.30)]
    buf = silence(0.74)
    for i, (f, at, dur) in enumerate(notes):
        gain = 0.78 + 0.07 * i                            # slight crescendo
        note = partial_note(f, dur, bell, tau=0.155)
        add(buf, [v * gain for v in note], at)
    buf = biquad(buf, 'lp', 7000.0, 0.707)                # soften the top end
    buf = biquad(buf, 'hp', 180.0, 0.707)
    return edge_fade(buf[:int(SR * 0.74)], 10.0), 0.74


# ---------- sfx_game_over : gentle descending resolve, ~560 ms -----------

def game_over():
    # A4 - F4 - D4: steps down and settles on the tonic. Soft, not punishing.
    notes = [(440.00, 0.00, 0.26), (349.23, 0.135, 0.28), (293.66, 0.270, 0.29)]
    # No sub-octave, and much stronger upper partials: A4/F4/D4 fundamentals
    # are near the bottom of what a phone speaker can move, so the melody has
    # to ride on harmonics 2-4.
    soft = [(1.0, 0.58, 1.00), (2.0, 0.78, 0.72),
            (3.0, 0.46, 0.52), (4.0, 0.22, 0.38), (5.0, 0.09, 0.30)]
    buf = silence(0.56)
    for i, (f, at, dur) in enumerate(notes):
        gain = 1.0 - 0.10 * i                             # fades as it falls
        note = partial_note(f, dur, soft, tau=0.195)
        # longer, rounder attack than the chime
        atk = int(SR * 0.014)
        for j in range(min(atk, len(note))):
            note[j] *= 0.5 - 0.5 * math.cos(math.pi * j / atk)
        add(buf, [v * gain for v in note], at)
    buf = biquad(buf, 'lp', 5200.0, 0.707)                # mellow, not dull
    buf = biquad(buf, 'hp', 240.0, 0.707)                 # bin the unplayable sub
    return edge_fade(buf[:int(SR * 0.56)], 12.0), 0.56


# ---------- sfx_shape_confirm : a tiny glint, ~230 ms ---------------------
#
# The solved shape's outline closing. Deliberately the smallest sound in the
# game: two high, soft plucks (G6 then D7) that read as "click into place" and
# leave the real reward - the win stinger a moment later - all the room it needs.

def shape_confirm():
    notes = [(1567.98, 0.000, 0.16), (2349.32, 0.065, 0.17)]
    glint = [(1.0, 1.00, 1.00), (2.0, 0.22, 0.55), (3.0, 0.08, 0.35)]
    buf = silence(0.23)
    for i, (f, at, dur) in enumerate(notes):
        note = partial_note(f, dur, glint, tau=0.062)
        add(buf, [v * (0.80 + 0.12 * i) for v in note], at)
    buf = biquad(buf, 'lp', 7600.0, 0.707)
    buf = biquad(buf, 'hp', 600.0, 0.707)                 # all of it rides on the phone speaker's best octaves
    return edge_fade(buf[:int(SR * 0.23)], 8.0), 0.23


# ---------- render --------------------------------------------------------

OUT = os.path.dirname(os.path.abspath(__file__))

# Assets are mastered close to full scale so nothing is thrown away before the
# mixer. The quiet-vs-loud balance is applied at playback by GameSound.gain,
# where it can be tuned without re-rendering. (The first cut normalised these
# to 0.52-0.88 and the result was inaudible on a phone.)
JOBS = [
    ('sfx_escape', escape, 0.95),
    ('sfx_blocked', blocked, 0.95),
    ('sfx_level_complete', level_complete, 0.95),
    ('sfx_game_over', game_over, 0.95),
    ('sfx_shape_confirm', shape_confirm, 0.95),
]

for name, fn, peak in JOBS:
    buf, dur = fn()
    path = os.path.join(OUT, name + '.wav')
    actual = write_wav(path, buf, peak)
    print(f'{name:20s} {actual * 1000:6.0f} ms  peak {peak:.2f}  {os.path.getsize(path):>7d} B wav')
