"""Generate the thread timeline diagrams for the deck (SVG)."""
import sys, os

NAVY = "#21295C"; SLATE = "#334155"; MUTED = "#64748B"; LANE = "#F1F5F9"; LINE = "#CBD5E1"
C = {"R1": "#065A82", "R2": "#047857", "R3": "#B45309"}
RED = "#E11D48"
FONT = "Carlito, Calibri, sans-serif"


class D:
    def __init__(self, w, h, units, label_w, x1, top, lane_h=104, bar_h=62):
        self.w, self.h, self.units, self.lw, self.x1, self.top = w, h, units, label_w, x1, top
        self.x0 = label_w + 30
        self.ppu = (x1 - self.x0) / units
        self.lane_h, self.bar_h = lane_h, bar_h
        self.out = []
        self.lanes = {}

    def x(self, t):
        return self.x0 + t * self.ppu

    def lane(self, key, label, sub=None):
        i = len(self.lanes)
        y = self.top + i * self.lane_h
        self.lanes[key] = y
        self.out.append(f'<rect x="{self.x0 - 10}" y="{y + 6}" width="{self.x1 - self.x0 + 20}" height="{self.lane_h - 12}" rx="10" fill="{LANE}"/>')
        ly = y + self.lane_h / 2 + (0 if sub else 11)
        self.out.append(f'<text x="{self.lw}" y="{ly}" font-size="30" font-weight="700" fill="{NAVY}" text-anchor="end">{label}</text>')
        if sub:
            self.out.append(f'<text x="{self.lw}" y="{ly + 32}" font-size="23" fill="{MUTED}" text-anchor="end">{sub}</text>')

    def bar(self, key, t0, t1, kind, color, text=None):
        y = self.lanes[key] + (self.lane_h - self.bar_h) / 2
        x0, x1 = self.x(t0) + 2, self.x(t1) - 2
        w = x1 - x0
        if kind == "run":
            self.out.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{self.bar_h}" rx="8" fill="{color}"/>')
            tc = "#FFFFFF"
        elif kind == "blocked":
            pid = "h" + color.strip("#")
            self.out.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{self.bar_h}" rx="8" fill="url(#{pid})" stroke="{color}" stroke-width="3"/>')
            tc = color
        elif kind == "parked":
            self.out.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{self.bar_h}" rx="8" fill="#FFFFFF" stroke="{color}" stroke-width="3" stroke-dasharray="10 7"/>')
            tc = color
        elif kind == "wait":
            self.out.append(f'<rect x="{x0}" y="{y}" width="{w}" height="{self.bar_h}" rx="8" fill="#FFFFFF" stroke="{MUTED}" stroke-width="3" stroke-dasharray="4 6"/>')
            tc = MUTED
        elif kind == "call":
            self.out.append(f'<rect x="{x0}" y="{y + 10}" width="{w}" height="{self.bar_h - 20}" rx="8" fill="{color}" fill-opacity="0.18" stroke="{color}" stroke-width="2"/>')
            tc = color
        elif kind.startswith("slot"):
            k = int(kind[4:]); sh = 26; sy = self.lanes[key] + 12 + k * (sh + 3)
            self.out.append(f'<rect x="{x0}" y="{sy}" width="{w}" height="{sh}" rx="6" fill="{color}" fill-opacity="0.18" stroke="{color}" stroke-width="2"/>')
            if text:
                self.out.append(f'<text x="{(x0 + x1) / 2}" y="{sy + 20}" font-size="20" font-weight="700" fill="{color}" text-anchor="middle">{text}</text>')
            return
        if text:
            fw = "700"
            bg = ""
            if kind in ("blocked",):
                # white pill behind the label so it reads over the hatching
                tw = len(text) * 13 + 24
                bg = f'<rect x="{(x0 + x1) / 2 - tw / 2}" y="{y + self.bar_h / 2 - 20}" width="{tw}" height="40" rx="6" fill="#FFFFFF"/>'
            self.out.append(bg + f'<text x="{(x0 + x1) / 2}" y="{y + self.bar_h / 2 + 10}" font-size="26" font-weight="{fw}" fill="{tc}" text-anchor="middle">{text}</text>')

    def done(self, t, req, row_key=None):
        self.dones = getattr(self, "dones", []) + [(t, req)]

    def note(self, x, y, text, color=SLATE, size=26, anchor="start", weight="400", italic=False):
        st = ' font-style="italic"' if italic else ""
        self.out.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{color}" text-anchor="{anchor}"{st}>{text}</text>')

    def axis(self, y, marks):
        self.out.append(f'<line x1="{self.x0}" y1="{y}" x2="{self.x1}" y2="{y}" stroke="{LINE}" stroke-width="3"/>')
        for t, lab in marks:
            self.out.append(f'<line x1="{self.x(t)}" y1="{y - 8}" x2="{self.x(t)}" y2="{y + 8}" stroke="{MUTED}" stroke-width="3"/>')
            self.out.append(f'<text x="{self.x(t)}" y="{y + 38}" font-size="24" fill="{MUTED}" text-anchor="middle">{lab}</text>')
        self.out.append(f'<text x="{self.x1}" y="{y - 16}" font-size="24" fill="{MUTED}" text-anchor="end">time →</text>')
        for t, req in getattr(self, "dones", []):
            self.out.append(f'<text x="{self.x(t)}" y="{y + 72}" font-size="25" font-weight="700" fill="{C[req]}" text-anchor="middle">{req} ✓</text>')

    def legend(self, y, kinds, x=None, gap=None):
        items = {
            "run": ("running on a CPU", "run"),
            "blocked": ("blocked: holds an OS thread, does nothing", "blocked"),
            "parked": ("parked: stack on the heap, carrier free", "parked"),
            "wait": ("waiting for a free thread", "wait"),
            "call": ("remote call in flight", "call"),
        }
        cx = self.x0 if x is None else x
        for k in kinds:
            label, kind = items[k]
            col = C["R1"]
            if kind == "run":
                s = f'<rect x="{cx}" y="{y}" width="54" height="34" rx="6" fill="{col}"/>'
            elif kind == "blocked":
                s = f'<rect x="{cx}" y="{y}" width="54" height="34" rx="6" fill="url(#h{col.strip("#")})" stroke="{col}" stroke-width="3"/>'
            elif kind == "parked":
                s = f'<rect x="{cx}" y="{y}" width="54" height="34" rx="6" fill="#FFF" stroke="{col}" stroke-width="3" stroke-dasharray="8 6"/>'
            elif kind == "wait":
                s = f'<rect x="{cx}" y="{y}" width="54" height="34" rx="6" fill="#FFF" stroke="{MUTED}" stroke-width="3" stroke-dasharray="4 5"/>'
            else:
                s = f'<rect x="{cx}" y="{y + 6}" width="54" height="22" rx="6" fill="{col}" fill-opacity="0.18" stroke="{col}" stroke-width="2"/>'
            self.out.append(s)
            self.out.append(f'<text x="{cx + 66}" y="{y + 26}" font-size="25" fill="{SLATE}">{label}</text>')
            step = 66 + len(label) * 12.2 + 50
            if gap == "rows":
                y += 50
            else:
                cx += step
                nxt = kinds[kinds.index(k) + 1] if kinds.index(k) + 1 < len(kinds) else None
                if nxt and cx + 66 + len(items[nxt][0]) * 12.2 > self.x1:
                    cx = self.x0 if x is None else x
                    y += 50

    def svg(self):
        pats = "".join(
            f'<pattern id="h{c.strip("#")}" width="16" height="16" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">'
            f'<rect width="16" height="16" fill="#FFFFFF"/><rect width="6" height="16" fill="{c}" fill-opacity="0.55"/></pattern>'
            for c in list(C.values()) + [RED, MUTED])
        body = "\n".join(self.out)
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}" font-family="{FONT}">'
                f'<defs>{pats}</defs><rect width="{self.w}" height="{self.h}" fill="#FFFFFF"/>\n{body}\n</svg>')


def wide(h):
    return D(1600, h, 16, 300, 1570, 20)


AX = [(0, "0"), (8, "8"), (10, "10"), (16, "16")]


def blocking():
    d = wide(720)
    d.lane("A", "Platform thread A"); d.lane("B", "Platform thread B")
    d.lane("Q", "Request queue", "no thread free"); d.lane("R", "Remote service")
    d.bar("A", 0, 1, "run", C["R1"], "R1"); d.bar("A", 1, 7, "blocked", C["R1"], "R1 blocked")
    d.bar("A", 7, 8, "run", C["R1"], "R1")
    d.bar("B", 1, 2, "run", C["R2"], "R2"); d.bar("B", 2, 8, "blocked", C["R2"], "R2 blocked")
    d.bar("B", 8, 9, "run", C["R2"], "R2")
    d.bar("Q", 2, 8, "wait", C["R3"], "R3 waits")
    d.bar("A", 8, 9, "run", C["R3"], "R3"); d.bar("A", 9, 15, "blocked", C["R3"], "R3 blocked")
    d.bar("A", 15, 16, "run", C["R3"], "R3")
    d.bar("R", 1, 7, "slot0", C["R1"], "R1 call"); d.bar("R", 2, 8, "slot1", C["R2"], "R2 call"); d.bar("R", 9, 15, "slot2", C["R3"], "R3 call")
    d.done(8, "R1"); d.done(9, "R2"); d.done(16, "R3")
    d.axis(470, AX)
    d.note(d.x0, 580, "R3 waits for a free thread: last response at t = 16.", NAVY, 32, weight="700")
    d.legend(620, ["run", "blocked", "wait", "call"])
    return d


def nonblocking():
    d = wide(520)
    d.lane("E", "Event loop", "one platform thread"); d.lane("R", "Remote service")
    for i, r in enumerate(["R1", "R2", "R3"]):
        d.bar("E", i, i + 1, "run", C[r], r)
        d.bar("E", i + 7, i + 8, "run", C[r], r)
        d.bar("R", i + 1, i + 7, f"slot{i}", C[r], f"{r} call")
    d.done(8, "R1"); d.done(9, "R2"); d.done(10, "R3")
    d.note((d.x(3) + d.x(7)) / 2, d.lanes["E"] + 62, "free", MUTED, 28, "middle", italic=True)
    d.axis(262, AX)
    d.note(d.x0, 372, "One thread never waits: all done at t = 10.", NAVY, 32, weight="700")
    d.note(d.x0, 414, "The price: the code is written as non-blocking pipelines.", MUTED, 26, italic=True)
    d.legend(450, ["run", "call"])
    return d


def virtual():
    d = wide(800)
    d.lane("C", "Carrier thread", "one platform thread")
    for i, r in enumerate(["R1", "R2", "R3"]):
        d.lane(r, f"Virtual thread {i + 1}")
    d.lane("R", "Remote service")
    for i, r in enumerate(["R1", "R2", "R3"]):
        d.bar("R", i + 1, i + 7, f"slot{i}", C[r], f"{r} call")
        d.bar("C", i, i + 1, "run", C[r], r); d.bar("C", i + 7, i + 8, "run", C[r], r)
        d.bar(r, i, i + 1, "run", C[r], r)
        d.bar(r, i + 1, i + 7, "parked", C[r], f"{r} parked")
        d.bar(r, i + 7, i + 8, "run", C[r], r)
    d.done(8, "R1"); d.done(9, "R2"); d.done(10, "R3")
    d.note((d.x(3) + d.x(7)) / 2, d.lanes["C"] + 62, "free", MUTED, 28, "middle", italic=True)
    d.axis(574, AX)
    d.note(d.x0, 684, "Plain blocking code, same timeline as the event loop: done at t = 10.", NAVY, 32, weight="700")
    d.legend(724, ["run", "parked", "call"])
    return d


def narrow(units, h):
    return D(950, h, units, 250, 930, 10, lane_h=86, bar_h=54)


AXN = [(0, "0"), (8, "8"), (10, "10")]


def pinning():
    d = narrow(10, 660)
    d.lane("C", "Carrier"); d.lane("V1", "Virtual thread 1", "in synchronized"); d.lane("V2", "Virtual thread 2"); d.lane("R", "Remote service")
    d.bar("C", 0, 1, "run", C["R1"], ""); d.bar("C", 1, 7, "blocked", RED, "stuck")
    d.bar("C", 7, 8, "run", C["R1"], ""); d.bar("C", 8, 9, "run", C["R2"], "")
    d.bar("V1", 0, 1, "run", C["R1"], ""); d.bar("V1", 1, 7, "blocked", C["R1"], "pinned")
    d.bar("V1", 7, 8, "run", C["R1"], "")
    d.bar("V2", 1, 8, "wait", C["R2"], "can't mount"); d.bar("V2", 8, 9, "run", C["R2"], "")
    d.bar("V2", 9, 10, "parked", C["R2"], "")
    d.bar("R", 1, 7, "call", C["R1"], "R1 call")
    d.axis(380, AXN)
    d.legend(460, ["run", "blocked", "wait", "parked"], gap="rows")
    return d


def cpu():
    d = narrow(10, 660)
    d.lane("C1", "Carrier 1"); d.lane("C2", "Carrier 2")
    d.lane("V1", "Virtual thread 1", "CPU-bound"); d.lane("V2", "Virtual thread 2", "CPU-bound"); d.lane("V3", "Virtual thread 3", "short I/O task")
    d.bar("C1", 0, 9, "run", C["R1"], "VT1"); d.bar("C1", 9, 10, "run", C["R3"], "")
    d.bar("C2", 1, 10, "run", C["R2"], "VT2")
    d.bar("V1", 0, 9, "run", C["R1"], "never yields"); d.bar("V2", 1, 10, "run", C["R2"], "never yields")
    d.bar("V3", 2, 9, "wait", C["R3"], "no carrier free"); d.bar("V3", 9, 10, "run", C["R3"], "")
    d.axis(470, [(0, "0"), (9, "9"), (10, "10")])
    d.legend(560, ["run", "wait"])
    return d


if __name__ == "__main__":
    out = sys.argv[1]
    for name, fn in [("blocking-io", blocking), ("non-blocking-io", nonblocking), ("virtual-threads", virtual), ("pinning", pinning), ("cpu-monopolization", cpu)]:
        open(os.path.join(out, f"threads-{name}.svg"), "w").write(fn().svg())
    print("ok")
