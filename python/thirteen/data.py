"""Training data written by `thirteen selfplay` (cpp/command/selfplay.cpp), and the training window.

Each .npz file holds N compact rows, one per (deal, seat), with M candidate slots per row (cpp/dataio/trainingrows.h):

    deal       uint8  [N, 52]      the four arrangements, this seat's first, then the following seats; each as its
                                   front, middle and back cards (card ids), each row in increasing order
    rules      float32 [N, 14]     the scoring rules, including the dealer cost (model.RULE_NAMES)
    pop        uint8  [N]          population id
    member     uint16 [N]          which population member set this seat's own hand (for diagnostics)
    count      uint8  [N]          number of used candidate slots
    cands      uint32 [N, M]       candidates as local masks (positions in the sorted hand), front | middle << 13

Like KataGo's binaryInputNCHWPacked, the window keeps rows in this form (about 190 bytes per row with M = 32) and
each batch is expanded when it is drawn; the keys and the exact targets come from the C++ engine
(libthirteen_data, cpp/dataio/trainingrows_c.cpp). expand returns:

    hand       uint8  [B, 13]      card ids in increasing order
    rules, pop, count              as above (pop and count as int32)
    masks      uint16 [B, M, 3]    candidates as position masks (front, middle, back)
    keys       uint32 [B, M, 3]    row hand keys
    targets    uint8  [B, M, 17]   training targets in units of 1/12 (model.TARGET_NAMES)
    result     float32 [B, M]      the candidate's exact result against the three actual opponents under the rules
    opp_royalty float32 [B, 7]     the opponents' royalties per type and their dragons (model.NUM_OPP_ROYALTIES)

Files from before the rules input store "c" [N] instead of rules; they load as the family rules with that c. Files
with 12 rules (before strict and wheel) load with both 0.
"""

import ctypes
import glob
import os

import numpy as np
import torch

from .model import FAMILY_RULES, NUM_OPP_ROYALTIES, NUM_RULES, NUM_TARGETS, TARGET_UNITS

STORED = {"deal": np.uint8, "rules": np.float32, "pop": np.uint8, "member": np.uint16, "count": np.uint8,
          "cands": np.uint32}
DEFAULT_LIB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "cpp", "build",
                           "libthirteen_data.so")
LOCAL_BITS = 13
LOCAL_ALL = (1 << LOCAL_BITS) - 1


class Expander:
    """Expands compact rows with libthirteen_data."""

    def __init__(self, lib_path=DEFAULT_LIB, threads=4):
        self.lib = ctypes.CDLL(os.path.abspath(lib_path))
        p = np.ctypeslib.ndpointer
        self.lib.thirteen_expand.restype = ctypes.c_int
        self.lib.thirteen_expand.argtypes = [
            ctypes.c_int64, ctypes.c_int, p(np.uint8, flags="C"), p(np.uint8, flags="C"), p(np.float32, flags="C"),
            p(np.uint32, flags="C"), p(np.uint32, flags="C"), p(np.uint8, flags="C"), p(np.float32, flags="C"),
            p(np.float32, flags="C"), ctypes.c_int]
        self.threads = threads

    def expand(self, rows):
        """Compact rows (a dict of the STORED arrays) -> the full arrays (see the module docstring)."""
        deal, count, rules, cands = (np.ascontiguousarray(rows[k]) for k in ("deal", "count", "rules", "cands"))
        n, m = cands.shape
        keys = np.empty((n, m, 3), np.uint32)
        targets = np.empty((n, m, NUM_TARGETS), np.uint8)
        result = np.empty((n, m), np.float32)
        opp_royalty = np.empty((n, NUM_OPP_ROYALTIES), np.float32)
        if self.lib.thirteen_expand(n, m, deal, count, rules, cands, keys, targets, result, opp_royalty, self.threads):
            raise ValueError("Bad training rows: count > candidate slots")
        used = np.arange(m)[None, :] < count[:, None]
        front = cands & LOCAL_ALL
        middle = (cands >> LOCAL_BITS) & LOCAL_ALL
        back = np.where(used, LOCAL_ALL & ~(front | middle), 0)
        return {
            "hand": np.sort(deal[:, :13], axis=1),
            "rules": rules,
            "pop": rows["pop"].astype(np.int32),
            "count": count.astype(np.int32),
            "masks": np.stack([front, middle, back], axis=-1).astype(np.uint16),
            "keys": keys,
            "targets": targets,
            "result": result,
            "opp_royalty": opp_royalty,
        }


def load_file(path):
    """The compact rows of one .npz file."""
    with np.load(path) as d:
        if "rules" not in d:
            rules = np.tile(np.array(FAMILY_RULES, np.float32), (d["c"].shape[0], 1))
            rules[:, 0] = d["c"]
            return {k: (rules if k == "rules" else d[k].astype(dtype, copy=False)) for k, dtype in STORED.items()}
        out = {k: d[k].astype(dtype, copy=False) for k, dtype in STORED.items()}
        if out["rules"].shape[1] < NUM_RULES:
            pad = np.zeros((out["rules"].shape[0], NUM_RULES - out["rules"].shape[1]), np.float32)
            out["rules"] = np.concatenate([out["rules"], pad], axis=1)
        return out


def compute_desired_num_rows(num_usable_rows, min_rows, add_to_data_rows, taper_window_exponent,
                             expand_window_per_row, taper_window_scale, max_rows):
    """The power-law training window size, copied from KataGo's python/shuffle.py (v1.18.2).

    The window starts at min_rows, grows by expand_window_per_row per new row at first, and then like
    rows ** taper_window_exponent. KataGo uses an exponent of 0.65 to 0.675 and expand_window_per_row 0.4.
    """
    window_taper_offset = taper_window_scale if taper_window_scale is not None else min_rows
    power_law_x = num_usable_rows - min_rows + window_taper_offset + add_to_data_rows
    unscaled_power_law = (power_law_x ** taper_window_exponent) - (window_taper_offset ** taper_window_exponent)
    scaled_power_law = unscaled_power_law / (taper_window_exponent * (window_taper_offset ** (taper_window_exponent - 1)))
    desired_num_rows = int(scaled_power_law * expand_window_per_row + min_rows)
    desired_num_rows = max(desired_num_rows, min_rows)
    if max_rows is not None:
        desired_num_rows = min(desired_num_rows, max_rows)
    return desired_num_rows


def list_files(dirs):
    """All finished .npz files under the given directories, newest first."""
    files = []
    for d in dirs:
        files.extend(glob.glob(os.path.join(d, "**", "*.npz"), recursive=True))
    files.sort(key=lambda f: os.path.getmtime(f), reverse=True)
    return files


def num_rows(path):
    with np.load(path) as d:
        return d["count"].shape[0]


class DataWindow:
    """The newest rows of the run, held in memory, with a power-law window size."""

    def __init__(self, dirs, min_rows=250_000, max_rows=None, taper_exponent=0.65, expand_per_row=0.4,
                 taper_scale=None, lib=DEFAULT_LIB, verbose=True):
        files = list_files(dirs)
        counts = [num_rows(f) for f in files]
        total = sum(counts)
        desired = compute_desired_num_rows(total, min_rows, 0, taper_exponent, expand_per_row, taper_scale, max_rows)
        chosen = []
        rows = 0
        for f, n in zip(files, counts):
            if rows >= desired:
                break
            chosen.append(f)
            rows += n
        if not chosen:
            raise ValueError(f"No data in {dirs}")
        parts = {k: [] for k in STORED}
        for f in chosen:
            a = load_file(f)
            for k in STORED:
                parts[k].append(a[k])
        self.arrays = {k: np.concatenate(v) for k, v in parts.items()}
        self.n = self.arrays["count"].shape[0]
        self.num_cands = self.arrays["cands"].shape[1]
        self.expander = Expander(lib)
        if verbose:
            print(f"Data: {total} rows in {len(files)} files; window {desired}; loaded {self.n} rows from "
                  f"{len(chosen)} files ({sum(a.nbytes for a in self.arrays.values()) / 1e9:.2f} GB)", flush=True)

    def batch(self, size, rng, device, augment=True):
        idx = rng.integers(0, self.n, size=size)
        return make_batch(self.expander.expand({k: v[idx] for k, v in self.arrays.items()}), rng, device, augment)


def make_batch(a, rng, device, augment):
    """Numpy rows -> dict of tensors. With augment, applies a random suit permutation to each row; hand keys and
    position masks do not depend on suits, so nothing else changes."""
    cards = a["hand"].astype(np.int64)
    if augment:
        perms = np.argsort(rng.random((cards.shape[0], 4)), axis=1)
        cards = (cards // 4) * 4 + np.take_along_axis(perms, cards % 4, axis=1)
    m = a["masks"].shape[1]
    t = lambda x, dtype: torch.from_numpy(np.ascontiguousarray(x)).to(device=device, dtype=dtype)
    return {
        "cards": t(cards, torch.int64),
        "rules": t(a["rules"], torch.float32),
        "pop": t(a["pop"], torch.int64),
        "masks": t(a["masks"].astype(np.int64), torch.int64),
        "keys": t(a["keys"].astype(np.int64), torch.int64),
        "targets": t(a["targets"], torch.float32) / TARGET_UNITS,
        "valid": t(np.arange(m)[None, :] < a["count"][:, None], torch.bool),
        "result": t(a["result"], torch.float32),
        "opp_royalty": t(a["opp_royalty"], torch.float32),
    }
