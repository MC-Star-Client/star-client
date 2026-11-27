#!/usr/bin/env python3
"""Reads real captured samples. Never manufactures measurements or positive deltas."""
import csv, sys
from collections import defaultdict
from pathlib import Path
if len(sys.argv) != 2:
    raise SystemExit('Usage: python3 scripts/benchmark/check_csv.py path/to/benchmark.csv')
rows = list(csv.DictReader(Path(sys.argv[1]).open(encoding='utf8', newline='')))
if not rows:
    raise SystemExit('No data')
by = defaultdict(list)
for r in rows:
    mode = r['mode'].upper()
    if mode not in ('CLIENT','VANILLA'):
        raise SystemExit(f'Unknown render path: {mode}')
    if int(r['frames']) < 20:
        raise SystemExit('Not enough frames')
    for field in ['avg_fps','one_percent_low','point_one_percent_low','p99_ms']:
        if float(r[field]) <= 0:
            raise SystemExit(f'Invalid {field}: {r[field]}')
    by[mode].append(r)
for mode, samples in by.items():
    tuples = {(r['frames'],r['avg_fps'],r['one_percent_low'],r['p99_ms']) for r in samples}
    print(f'{mode}: {len(samples)} runs; distinct summaries: {len(tuples)}')
    if len(samples)>1 and len(tuples)==1:
        print('WARNING: identical runs detected; investigate capture and replay')
if not by['CLIENT']:
    print('No measured CLIENT renderer data. FPS ratio cannot be calculated.')
elif not by['VANILLA']:
    print('No VANILLA baseline. FPS ratio cannot be calculated.')
else:
    if any(r['world_seed']=='UNKNOWN' or r['route_id'].endswith('UNVERIFIED') for r in rows):
        print('WARNING: not a reproducible fixed-world, fixed-route comparison; no performance claim justified')
    from statistics import mean
    av = mean(float(r['avg_fps']) for r in by['VANILLA'])
    ac = mean(float(r['avg_fps']) for r in by['CLIENT'])
    print(f'Observed ratio (not a validated improvement): {ac / av:.3f}x')
