"""Map cpfair/quran-tajweed annotations from the 2017 Tanzil text onto our Tanzil text, and check them.

The annotations (github.com/cpfair/quran-tajweed, CC BY 4.0) give character positions in a 2017
copy of the Tanzil Uthmani text. Our copy differs slightly (e.g. it keeps tatweel), so each ayah
of the two texts is compared and every position is moved to the same character in ours. Marks
whose characters changed are dropped, and every kept mark is checked to sit on the letters its
rule is about (e.g. qalqalah on qaf/ta/ba/jim/dal). Our Quran text itself is never changed.

Usage: python map_tajweed.py OUR_XML TAJWEED_JSON TEXT_2017 [OUT_TXT]

  OUR_XML       composeApp/src/commonMain/composeResources/files/quran_uthmani.xml
  TAJWEED_JSON  output/tajweed.hafs.uthmani-pause-sajdah.json from cpfair/quran-tajweed
  TEXT_2017     the 2017 text linked from that repo's README (quran-uthmani.txt)
  OUT_TXT       composeApp/src/commonMain/composeResources/files/tajweed.txt
"""
import json, re, sys, collections, difflib
sys.stdout.reconfigure(encoding='utf-8')

xml_path, taj_path, old_path = sys.argv[1:4]
out_path = sys.argv[4] if len(sys.argv) > 4 else None

ours = {}
sura = 0
for line in open(xml_path, encoding='utf-8').read().splitlines():
    m = re.search(r'<sura index="(\d+)"', line)
    if m: sura = int(m.group(1))
    m = re.search(r'<aya index="(\d+)" text="([^"]*)"', line)
    if m: ours[(sura, int(m.group(1)))] = m.group(2)

old = {}
for line in open(old_path, encoding='utf-8-sig').read().splitlines():
    parts = line.split('|')
    if len(parts) == 3 and parts[0].isdigit():
        old[(int(parts[0]), int(parts[1]))] = parts[2]

NOON_TANWEEN = set('نًࣰٌࣱٍࣲ')
def check(rule, span):
    if rule == 'hamzat_wasl': return 'ٱ' in span
    if rule == 'lam_shamsiyyah': return 'ل' in span
    if rule == 'qalqalah': return any(c in span for c in 'قطبجد')
    if rule == 'ghunnah': return any(c in span for c in 'نم')
    if rule in ('ikhfa_shafawi', 'idghaam_shafawi'): return 'م' in span
    if rule == 'iqlab': return any(c in span for c in NOON_TANWEEN | set('ۭۢ'))
    if rule.startswith('madd'): return any(c in span for c in 'اويىٰۦۥٰٓۦۥ')
    return len(span.strip()) > 0

data = json.load(open(taj_path, encoding='utf-8'))
base_bad = mapped_bad = unmapped = total = 0
changed_ayat = 0
out = []
for entry in data:
    key = (entry['surah'], entry['ayah'])
    o, n = old[key], ours[key]
    if o != n: changed_ayat += 1
    # Map every position of the old text that is unchanged in ours.
    pos = {}
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, o, n, autojunk=False).get_opcodes():
        if tag == 'equal':
            for k in range(i2 - i1): pos[i1 + k] = j1 + k
    kept = []
    for a in entry['annotations']:
        total += 1
        s, e, r = a['start'], a['end'], a['rule']
        if not check(r, o[s:e]): base_bad += 1
        if s not in pos or (e - 1) not in pos:
            unmapped += 1; continue
        ns, ne = pos[s], pos[e - 1] + 1
        if not check(r, n[ns:ne]): mapped_bad += 1; continue
        kept.append({'rule': r, 'start': ns, 'end': ne})
    out.append({'surah': key[0], 'ayah': key[1], 'annotations': kept})

print(f'ayat whose text differs between 2017 and ours: {changed_ayat} of {len(data)}')
print(f'annotations: {total}')
print(f'  not on expected letters in the 2017 text itself: {base_bad} ({100*base_bad/total:.2f}%)')
print(f'  could not be mapped (their letters changed): {unmapped} ({100*unmapped/total:.2f}%)')
print(f'  mapped but on unexpected letters in ours: {mapped_bad} ({100*mapped_bad/total:.2f}%)')
kept_n = sum(len(x['annotations']) for x in out)
print(f'  kept: {kept_n} ({100*kept_n/total:.2f}%)')
if out_path:
    header = [
        'Tajweed marks for the Tanzil Uthmani text bundled with Iqra.',
        'Source: github.com/cpfair/quran-tajweed, licensed CC BY 4.0 (creativecommons.org/licenses/by/4.0/).',
        'Changed: positions moved from the 2017 Tanzil text to the current one by',
        'tools/tajweed/map_tajweed.py; marks whose letters changed were dropped.',
        'Format: surah|ayah|rule:start:end,... (positions are code points in the ayah text)',
    ]
    lines = ['# ' + h for h in header]
    for x in out:
        if x['annotations']:
            marks = ','.join(f"{a['rule']}:{a['start']}:{a['end']}" for a in x['annotations'])
            lines.append(f"{x['surah']}|{x['ayah']}|{marks}")
    with open(out_path, 'w', encoding='utf-8', newline='') as f:
        f.write(chr(10).join(lines) + chr(10))
    print('written', out_path)
