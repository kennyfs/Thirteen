"""The network: scores every candidate arrangement of a 13-card hand.

Inputs are raw integers, the same ones the C++ engine has, so the C++ side needs no feature code:

    cards  int64 [B, 13]     card ids (rank * 4 + suit, rank 0 = 2 ... 12 = A, suit 0..3), in increasing order
    rules  float32 [B, 14]   the scoring rules (RULE_NAMES, Rules::toFeatures in cpp/game/rules.h), including c
    pop    int64 [B]         which opponent population to respond to (an index into an embedding)
    masks  int64 [B, K, 3]   per candidate, 13-bit masks of card positions in the front, middle and back rows
    keys   int64 [B, K, 3]   per candidate, the hand keys of the three rows (cpp/game/handeval.h)

Design: a Transformer encodes the 13 cards plus a global token once per hand; that is where most of the compute goes.
Each candidate is then scored cheaply: its first hidden layer is a sum, over its rows, of per-card projections
(computed once per hand), plus a projection of each row's hand-key features and of the global token. So the cost per
candidate is a few small matrix products, which matters because a hand has up to a few thousand candidates.

Outputs, per candidate, are probabilities of the scoring events (see Scoring::Target in cpp/game/scoring.h), and per
hand the opponents' expected royalties. None of them depend on the rules except through how the opponents play, so
the expected result Q is composed from them exactly by the rules (compose_q, the same as Scoring::compose):

    Q = 3 scoop_point (scoop - scooped) + 3 home_run_point (home_run - opp_home_run) - c dealer_share
        + 3 row_point sum_r (row_win_r - row_lose_r) + my royalties and dragon - the opponents' royalties and dragons
"""

import math
from dataclasses import dataclass, asdict

import torch
import torch.nn as nn
import torch.nn.functional as F

NUM_CARDS_IN_HAND = 13
NUM_ROWS = 3
ROW_SIZES = (3, 5, 5)
TARGET_NAMES = (
    "scoop", "scooped", "home_run", "opp_home_run", "dealer_share",
    "row_win_front", "row_win_middle", "row_win_back", "row_lose_front", "row_lose_middle", "row_lose_back",
    "royalty_lost_front_trips", "royalty_lost_middle_full_house", "royalty_lost_middle_quads",
    "royalty_lost_middle_straight_flush", "royalty_lost_back_quads", "royalty_lost_back_straight_flush",
)
NUM_TARGETS = len(TARGET_NAMES)
LEGACY_NUM_TARGETS = 11  # Before the rules input.
RULE_NAMES = (
    "c", "row", "scoop", "home_run",
    "front_trips", "middle_full_house", "middle_quads", "middle_straight_flush", "back_quads", "back_straight_flush",
    "on_win", "dragon", "strict", "wheel",
)
NUM_RULES = len(RULE_NAMES)
# Typical sizes, to scale the rules input of the network.
RULE_SCALES = (1.0, 1.0, 3.0, 6.0, 3.0, 2.0, 8.0, 10.0, 4.0, 5.0, 1.0, 36.0, 1.0, 1.0)
# Royalty types (Royalty::Type): row and category.
ROYALTY_ROW_CATEGORY = ((0, 4), (1, 7), (1, 8), (1, 9), (2, 8), (2, 9))
NUM_ROYALTIES = len(ROYALTY_ROW_CATEGORY)
NUM_OPP_ROYALTIES = NUM_ROYALTIES + 1  # And the dragon.
FAMILY_RULES = (0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0)  # With c = 0.
TARGET_UNITS = 12  # Targets are stored as uint8 in units of 1/12.
NUM_CATEGORIES = 9
KEY_FEATURES = NUM_CATEGORIES + 5  # Category one-hot and the five tie-break ranks.


@dataclass
class ModelConfig:
    d_model: int = 128
    num_layers: int = 4
    num_heads: int = 4
    ff_mult: int = 4
    hidden: int = 256
    num_pops: int = 8

    def to_dict(self):
        return asdict(self)


class Block(nn.Module):
    """Pre-norm Transformer block, written with basic ops so that it exports cleanly to ONNX."""

    def __init__(self, d, heads, ff_mult):
        super().__init__()
        self.heads = heads
        self.norm1 = nn.LayerNorm(d)
        self.qkv = nn.Linear(d, 3 * d)
        self.proj = nn.Linear(d, d)
        self.norm2 = nn.LayerNorm(d)
        self.ff1 = nn.Linear(d, ff_mult * d)
        self.ff2 = nn.Linear(ff_mult * d, d)

    def forward(self, x):
        b, t, d = x.shape
        dh = d // self.heads
        qkv = self.qkv(self.norm1(x)).reshape(b, t, 3, self.heads, dh).permute(2, 0, 3, 1, 4)
        q, k, v = qkv[0], qkv[1], qkv[2]
        att = torch.softmax(torch.matmul(q, k.transpose(-1, -2)) * (1.0 / math.sqrt(dh)), dim=-1)
        out = torch.matmul(att, v).permute(0, 2, 1, 3).reshape(b, t, d)
        x = x + self.proj(out)
        x = x + self.ff2(F.gelu(self.ff1(self.norm2(x))))
        return x


def key_features(keys):
    """keys: int64 [..., 3] -> (features float [..., 3, KEY_FEATURES], categories float [..., 3])."""
    k = keys.to(torch.float32)  # Keys are < 2^24, exact in float32.
    cat = torch.floor(k / float(1 << 20))
    onehot = (cat.unsqueeze(-1) == torch.arange(1, NUM_CATEGORIES + 1, dtype=torch.float32, device=keys.device))
    nibbles = []
    for i in range(5):
        x = torch.floor(k / float(1 << (16 - 4 * i)))
        nibbles.append((x - 16.0 * torch.floor(x / 16.0)) / 12.0)
    feats = torch.cat([onehot.to(torch.float32), torch.stack(nibbles, dim=-1)], dim=-1)
    return feats, cat


def mask_bits(masks):
    """masks: int64 [B, K, 3] -> float [B, K, 3, 13], 1 where the card at that position is in that row."""
    m = masks.to(torch.float32).unsqueeze(-1)
    pow2 = torch.pow(2.0, torch.arange(NUM_CARDS_IN_HAND, dtype=torch.float32, device=masks.device))
    x = torch.floor(m / pow2)
    return x - 2.0 * torch.floor(x / 2.0)


class ThirteenNet(nn.Module):
    def __init__(self, config: ModelConfig):
        super().__init__()
        self.config = config
        d = config.d_model
        h = config.hidden
        self.rank_emb = nn.Embedding(13, d)
        self.suit_emb = nn.Embedding(4, d)
        self.global_token = nn.Parameter(torch.zeros(d))
        self.rules_proj = nn.Linear(NUM_RULES, d)
        self.register_buffer("rule_scales", torch.tensor(RULE_SCALES), persistent=False)
        self.pop_emb = nn.Embedding(config.num_pops, d)
        self.blocks = nn.ModuleList([Block(d, config.num_heads, config.ff_mult) for _ in range(config.num_layers)])
        self.final_norm = nn.LayerNorm(d)
        # Per-row projections of the card embeddings, and of the row's key features, into the first hidden layer.
        self.card_proj = nn.Linear(d, NUM_ROWS * h, bias=False)
        self.key_proj = nn.ModuleList([nn.Linear(KEY_FEATURES, h, bias=False) for _ in range(NUM_ROWS)])
        self.global_proj = nn.Linear(d, h)
        self.hidden2 = nn.Linear(h, h)
        self.out = nn.Linear(h, NUM_TARGETS)
        self.opp_royalty_head = nn.Linear(d, NUM_OPP_ROYALTIES)

    def encode(self, cards, rules, pop):
        """-> (global [B, d], card embeddings [B, 13, d])."""
        rank = torch.div(cards, 4, rounding_mode="trunc")
        suit = cards - 4 * rank
        x = self.rank_emb(rank) + self.suit_emb(suit)
        g = self.global_token + self.rules_proj(rules / self.rule_scales) + self.pop_emb(pop)
        x = torch.cat([g.unsqueeze(1), x], dim=1)
        for block in self.blocks:
            x = block(x)
        x = self.final_norm(x)
        return x[:, 0], x[:, 1:]

    def forward(self, cards, rules, pop, masks, keys):
        """-> (q [B, K], probs [B, K, NUM_TARGETS], logits [B, K, NUM_TARGETS], opp_royalty [B, NUM_OPP_ROYALTIES])."""
        g, z = self.encode(cards, rules, pop)
        b = z.shape[0]
        h = self.config.hidden
        per_card = self.card_proj(z).reshape(b, NUM_CARDS_IN_HAND, NUM_ROWS, h)
        bits = mask_bits(masks)
        feats, cats = key_features(keys)
        pre = self.global_proj(g).unsqueeze(1)
        for r in range(NUM_ROWS):
            pre = pre + torch.matmul(bits[:, :, r, :], per_card[:, :, r, :]) * (1.0 / ROW_SIZES[r])
            pre = pre + self.key_proj[r](feats[:, :, r, :])
        x = F.relu(pre)
        x = F.relu(self.hidden2(x))
        logits = self.out(x)
        probs = torch.sigmoid(logits)

        opp_royalty = F.softplus(self.opp_royalty_head(g))
        q = compose_q(rules, cards, cats, probs.float(), opp_royalty.float())
        return q, probs, logits, opp_royalty


def compose_q(rules, cards, cats, p, opp_royalty):
    """The expected result from event probabilities p [B, K, NUM_TARGETS] and the opponents' expected royalties
    [B, NUM_OPP_ROYALTIES] under rules [B, NUM_RULES]; the same as Scoring::compose. cats: row categories [B, K, 3]."""
    rule = lambda name: rules[:, RULE_NAMES.index(name)].unsqueeze(-1)
    on_win = rule("on_win")
    q = (3.0 * rule("scoop") * (p[..., 0] - p[..., 1]) + 3.0 * rule("home_run") * (p[..., 2] - p[..., 3])
         - rule("c") * p[..., 4])
    for r in range(NUM_ROWS):
        win, lose = p[..., 5 + r], p[..., 8 + r]
        q = q + 3.0 * rule("row") * (win - lose)
        mine = torch.zeros_like(win)
        for t, (row, cat) in enumerate(ROYALTY_ROW_CATEGORY):
            if row == r:
                mine = mine + (cats[..., r] == cat).to(win.dtype) * rules[:, 4 + t].unsqueeze(-1)
        q = q + 3.0 * mine * (on_win * win + (1.0 - on_win))
    royalties = rules[:, 4:4 + NUM_ROYALTIES]
    q = q - 3.0 * on_win * (p[..., 11:11 + NUM_ROYALTIES] * royalties.unsqueeze(1)).sum(-1)
    q = q - (1.0 - on_win) * (opp_royalty[:, :NUM_ROYALTIES] * royalties).sum(-1, keepdim=True)
    # A dragon: in increasing card order the ranks are exactly 0..12.
    rank = torch.div(cards, 4, rounding_mode="trunc")
    dragon = (rank == torch.arange(13, device=cards.device)).to(p.dtype).prod(dim=-1, keepdim=True)
    q = q + rule("dragon") * (3.0 * dragon - opp_royalty[:, NUM_ROYALTIES:])
    return q


def upgrade_legacy(state_dict):
    """A state dict from an older network -> one for ThirteenNet that plays the same under the family rules:
      - before the rules input (c_proj, 11 targets, opp_bonus_head): the dealer cost keeps its weights, the other rules
        start at zero weight, and the new outputs start untrained;
      - with fewer rules (12, before strict and wheel): the new rules start at zero weight."""
    w = state_dict.get("rules_proj.weight")
    if w is not None and w.shape[1] < NUM_RULES:
        s = dict(state_dict)
        s["rules_proj.weight"] = torch.cat(
            [w, torch.zeros(w.shape[0], NUM_RULES - w.shape[1], dtype=w.dtype, device=w.device)], dim=1)
        return s
    if "c_proj.weight" not in state_dict:
        return state_dict
    s = dict(state_dict)
    w, b = s.pop("c_proj.weight"), s.pop("c_proj.bias")
    d = w.shape[0]
    s["rules_proj.weight"] = torch.cat([w * RULE_SCALES[0], torch.zeros(d, NUM_RULES - 1, dtype=w.dtype, device=w.device)], dim=1)
    s["rules_proj.bias"] = b
    # The family rules' other features would add rules_proj(FAMILY_RULES / scales) = 0 with zero weights.
    ow, ob = s.pop("out.weight"), s.pop("out.bias")
    new_w = torch.zeros(NUM_TARGETS, ow.shape[1], dtype=ow.dtype, device=ow.device)
    new_b = torch.full((NUM_TARGETS,), -4.0, dtype=ob.dtype, device=ob.device)  # Royalty-lost events are rare.
    new_w[:LEGACY_NUM_TARGETS] = ow
    new_b[:LEGACY_NUM_TARGETS] = ob
    s["out.weight"], s["out.bias"] = new_w, new_b
    hw, hb = s.pop("opp_bonus_head.weight"), s.pop("opp_bonus_head.bias")
    s["opp_royalty_head.weight"] = torch.zeros(NUM_OPP_ROYALTIES, hw.shape[1], dtype=hw.dtype, device=hw.device)
    s["opp_royalty_head.bias"] = torch.full((NUM_OPP_ROYALTIES,), -3.0, dtype=hb.dtype, device=hb.device)
    return s


def save_checkpoint(path, model, optimizer=None, extra=None):
    state = {"config": model.config.to_dict(), "model": model.state_dict()}
    if optimizer is not None:
        state["optimizer"] = optimizer.state_dict()
    if extra:
        state.update(extra)
    tmp = path + ".tmp"
    torch.save(state, tmp)
    import os
    os.replace(tmp, path)


def load_checkpoint(path, device="cpu"):
    state = torch.load(path, map_location=device, weights_only=False)
    model = ThirteenNet(ModelConfig(**state["config"])).to(device)
    w = state["model"].get("rules_proj.weight")
    if "c_proj.weight" in state["model"] or (w is not None and w.shape[1] < NUM_RULES):
        # An older checkpoint: its optimizer state does not fit the new shapes.
        state.pop("optimizer", None)
    model.load_state_dict(upgrade_legacy(state["model"]))
    if "ema" in state:
        state["ema"] = upgrade_legacy(state["ema"])
    return model, state


def export_onnx(model, path, opset=17):
    """Exports with dynamic batch and candidate counts. Outputs: q, probs, opp_royalty."""

    class Wrapper(nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, cards, rules, pop, masks, keys):
            q, probs, _, opp_royalty = self.m(cards, rules, pop, masks, keys)
            return q, probs, opp_royalty

    model = model.eval().to("cpu")
    b, k = 2, 7
    cards = torch.arange(13, dtype=torch.int64).repeat(b, 1) * 4
    rules = torch.tensor(FAMILY_RULES).repeat(b, 1)
    pop = torch.zeros(b, dtype=torch.int64)
    masks = torch.tensor([7, 31 << 3, 31 << 8], dtype=torch.int64).repeat(b, k, 1)
    keys = torch.zeros(b, k, 3, dtype=torch.int64)
    tmp = path + ".tmp"
    torch.onnx.export(
        Wrapper(model), (cards, rules, pop, masks, keys), tmp,
        input_names=["cards", "rules", "pop", "masks", "keys"],
        output_names=["q", "probs", "opp_royalty"],
        dynamic_axes={
            "cards": {0: "batch"}, "rules": {0: "batch"}, "pop": {0: "batch"},
            "masks": {0: "batch", 1: "cands"}, "keys": {0: "batch", 1: "cands"},
            "q": {0: "batch", 1: "cands"}, "probs": {0: "batch", 1: "cands"}, "opp_royalty": {0: "batch"},
        },
        opset_version=opset,
        dynamo=False,
    )
    import os
    os.replace(tmp, path)
