#!/usr/bin/env python3
"""Read actual F9 CSVs. Never claim proven FPS gains without >=5 runs per mode."""
import argparse, csv, statistics
from collections import defaultdict

def read(path):
    with open(path, encoding='utf-8') as stream:
        return list(csv.DictReader(stream))

def summary(rows, label):
    groups=defaultdict(list)
    for row in rows:
        try:
            mode=row['mode'].strip().upper()
            if mode in ('VANILLA','CLIENT'):
                groups[mode].append(float(row['avg_fps']))
        except (ValueError, KeyError):
            pass
    v=groups['VANILLA']; c=groups['CLIENT']
    print(f'\n{label}: VANILLA n={len(v)} CLIENT n={len(c)}')
    if not v or not c:
        print('Keine A/B-Differenz berechenbar.'); return None
    av=statistics.mean(v); ac=statistics.mean(c)
    delta=(ac/av-1)*100
    print(f'Vanilla Ø {av:.2f} FPS | Client Ø {ac:.2f} FPS | Delta {delta:+.2f}% | Ziel {3*av:.2f} FPS')
    if min(len(v),len(c))<5:
        print('KEIN belastbarer Nachweis: weniger als 5 Läufe je Modus.')
    print('Manuelle Route ist nicht identisch garantiert; selbst 5+ Läufe beweisen ohne gleiche Szene keine Kausalität.')
    return av,ac

def phases(path):
    rows=read(path)
    ranked=[]
    exclude={'timestamp','mode','run','frames','visible_sections_avg','chunk_draw_calls_avg','gpu_wait_us','gpu_time_us','notes'}
    for key in rows[0] if rows else []:
        if key in exclude or not key.endswith('_us'): continue
        values=[]
        for row in rows:
            if row['mode'].upper()!='CLIENT': continue
            try: values.append(float(row[key]))
            except (ValueError,KeyError): pass
        if values: ranked.append((statistics.mean(values),key,len(values)))
    overall={'game_extract_us','level_extract_us','world_render_us','game_render_us'}
    print('\nEinschließende Hauptphasen (NICHT als eigene Arbeit zu den Teilphasen addieren):')
    for val,name,n in sorted((v,k,c) for v,k,c in ranked if k in overall)[::-1]:
        print(f'  {name:28s} {val:11.3f} µs/Frame ({n} Läufe)')
    print('\nGemessene Teilphasen (CPU-Wandzeiten; kein GPU-Timing):')
    for val,name,n in sorted((v,k,c) for v,k,c in ranked if k not in overall)[::-1]:
        print(f'  {name:28s} {val:11.3f} µs/Frame ({n} Läufe)')
    print('Draw-Calls und GPU-Wartezeit: ungemessen, leere CSV-Felder sind keine Null.')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--full',required=True)
    p.add_argument('--half')
    p.add_argument('--phases')
    args=p.parse_args()
    full=summary(read(args.full),'Volle Auflösung')
    half=summary(read(args.half),'Halbe Auflösung') if args.half else None
    if full and half:
        r=half[0]/full[0]
        print(f'\nVanilla-FPS-Verhältnis halbe/volle Auflösung: {r:.2f}×')
        if r>1.25:
            print('Auflösungssensitiv: möglicher GPU-/Fragmentkostenanteil; GPU-Limit NICHT verifiziert.')
        else:
            print('Auflösung weniger sensitiv: möglicher CPU-/Draw-Overhead; GPU-Limit NICHT ausgeschlossen.')
    if args.phases: phases(args.phases)
