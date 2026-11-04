#!/usr/bin/env python3
"""Stores a network's weights as float16, computing in float32 as before: about half the file.

    python fp16_weights.py run/models/net_5.onnx net_5.fp16.onnx

Each float32 initializer of 256 values or more is stored as float16 and cast back to float32 by a Cast node, which ONNX
Runtime folds when it loads the network: the network then runs as the float32 one does, with its weights rounded to
float16 (Q within ~0.004, chances within ~0.0003 of the float32 network's over 8M candidates; the same arrangement
in 99.97% of hands). Converting the whole network to float16 does not work: its card features divide by 2^20 and 2^16,
which float16 cannot hold. The engine and the app load either kind.
"""

import argparse

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper


def fp16_weights(model, min_size=256):
    g = model.graph
    initializers, casts = [], []
    for t in g.initializer:
        a = numpy_helper.to_array(t)
        if a.dtype == np.float32 and a.size >= min_size:
            h = numpy_helper.from_array(a.astype(np.float16), t.name + "_fp16")
            initializers.append(h)
            casts.append(helper.make_node("Cast", [h.name], [t.name], to=TensorProto.FLOAT, name=t.name + "_cast"))
        else:
            initializers.append(t)
    del g.initializer[:]
    g.initializer.extend(initializers)
    nodes = casts + list(g.node)
    del g.node[:]
    g.node.extend(nodes)
    onnx.checker.check_model(model)
    return model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("output")
    args = ap.parse_args()
    onnx.save(fp16_weights(onnx.load(args.input)), args.output)


if __name__ == "__main__":
    main()
