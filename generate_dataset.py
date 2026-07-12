

import random, string, hashlib, json, os, math, time

random.seed(42)
OUTPUT_DIR = "./datasets"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Kích thước benchmark: từ 1K đến 2M
BENCHMARK_SIZES = [1_000, 5_000, 10_000, 25_000, 50_000,
                   100_000, 250_000, 500_000, 1_000_000, 2_000_000]
QUERY_SIZE = 5_000  # số negative queries để đo FP


# ────────────────────────────────────────────────
# CÁC HÀM SINH DATASET
# ────────────────────────────────────────────────

def gen_username(n):
    """Dataset 1: Username strings (~12 bytes/elem)
    Use-case: Kiểm tra username đã tồn tại (GitHub, Discord)
    """
    pfx = ['user','cool','dark','fire','neo','max','pro','ace','top','big',
           'fast','red','ice','sky','sea','sun','raw','pro','old','new']
    noun = ['wolf','hawk','lion','tiger','shark','eagle','fox','bear','owl',
            'rock','star','moon','tree','wind','rain','snow','dust','sand']
    items = set()
    i = 0
    while len(items) < n:
        t = i % 3
        if t == 0:
            items.add(random.choice(pfx)+random.choice(noun)+str(random.randint(1,9999)))
        elif t == 1:
            items.add(''.join(random.choices(
                string.ascii_lowercase+string.digits, k=random.randint(7,14))))
        else:
            items.add('u'+str(random.randint(100000, 99999999)))
        i += 1
    return list(items)[:n]


def gen_url(n):
    """Dataset 2: URL strings (~65 bytes/elem)
    Use-case: URL blacklist / spam filter (web proxy)
    """
    domains = ['bad.com','evil.net','spam.org','virus.io','hack.cc',
               'malware.ru','phish.biz','trojan.xyz']
    items = set()
    while len(items) < n:
        d = random.choice(domains)
        depth = random.randint(1, 3)
        path = '/'.join([''.join(random.choices(
            string.ascii_lowercase+string.digits+'-', k=random.randint(3,10)))
            for _ in range(depth)])
        items.add(f"https://{d}/{path}")
    return list(items)[:n]


def gen_int_id(n):
    """Dataset 3: Integer IDs (8 bytes/elem)
    Use-case: Transaction idempotency check (payment systems)
    """
    items = set()
    base = 1_000_000_000
    while len(items) < n:
        # Mix: sequential-ish cluster + random large range
        if random.random() < 0.6:
            items.add(base + random.randint(0, n * 20))
        else:
            items.add(random.randint(1, 2**40))
    return list(items)[:n]


def gen_sha256_hash(n):
    """Dataset 4: SHA-256 hashes (64 bytes/elem)
    Use-case: Password breach detection (HaveIBeenPwned-style)
    """
    items = set()
    i = 0
    while len(items) < n:
        raw = f"password_seed_{i}_{random.random()}"
        items.add(hashlib.sha256(raw.encode()).hexdigest())
        i += 1
    return list(items)[:n]


def make_negatives(inserted_set, n_neg, prefix="ZZZQRY"):
    """Sinh tập pure-negative queries (guaranteed NOT in inserted_set)"""
    neg = []
    i = 0
    while len(neg) < n_neg:
        candidate = f"{prefix}_{i}_{random.randint(0, 999999)}"
        if candidate not in inserted_set:
            neg.append(candidate)
        i += 1
    return neg


# ────────────────────────────────────────────────
# BẢNG THAM SỐ LÝ THUYẾT
# ────────────────────────────────────────────────

def bloom_params(n, fpr=0.01):
    """Công thức Mitzenmacher cho Bloom Filter"""
    m = math.ceil(-n * math.log(fpr) / (math.log(2)**2))
    k = max(1, round((m/n) * math.log(2)))
    actual_fpr = (1 - math.exp(-k*n/m))**k
    return m, k, actual_fpr


def print_theory_tables():
    print("\n" + "="*70)
    print("  BẢNG LÝ THUYẾT BLOOM FILTER — Mitzenmacher (FPR target = 1%)")
    print("  m = -n·ln(p)/(ln2)²  |  k = (m/n)·ln2")
    print("="*70)
    print(f"{'N':>10} | {'m (bits)':>12} | {'m (KB)':>8} | {'k':>3} | {'FPR theory':>12}")
    print("-"*55)
    for n in BENCHMARK_SIZES:
        m, k, fpr = bloom_params(n)
        print(f"{n:>10,} | {m:>12,} | {m/8/1024:>8.1f} | {k:>3} | {fpr*100:>11.4f}%")

    print("\n" + "="*70)
    print("  MEMORY BUDGET: BF vs HT (HT ≈ 100 bytes/elem, BF ≈ 1.2 bytes/elem)")
    print("="*70)
    print(f"{'N':>10} | {'BF (KB)':>8} | {'HT (KB)':>8} | {'Ratio':>8} | {'BF saves':>10}")
    print("-"*55)
    for n in BENCHMARK_SIZES:
        m, k, _ = bloom_params(n)
        bf_kb = m/8/1024
        # 100 bytes/elem = HashTableSC.estimateMemoryBytes() model: table
        # array ref (~10.7B, capacity/loadfactor-adjusted) + node object
        # (32B) + String object (~52B incl. ~12-char payload) per element.
        ht_kb = (n * 100) / 1024
        print(f"{n:>10,} | {bf_kb:>8.1f} | {ht_kb:>8.1f} | {ht_kb/bf_kb:>7.1f}x | {(1-bf_kb/ht_kb)*100:>9.1f}%")


# ────────────────────────────────────────────────
# MAIN GENERATION
# ────────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 60)
    print("  RBL DATASET GENERATOR v1.0")
    print("  Hash Table vs Bloom Filter Crossover Analysis")
    print("=" * 60)

    # 1. USERNAME benchmark (10 sizes)
    print("\n[1/2] Generating USERNAME benchmark datasets:")
    for size in BENCHMARK_SIZES:
        t0 = time.time()
        inserted = gen_username(size)
        inserted_set = set(inserted)
        negatives = make_negatives(inserted_set, QUERY_SIZE)

        positives = random.sample(inserted, min(QUERY_SIZE//2, len(inserted)))
        mixed = positives + negatives[:QUERY_SIZE//2]
        random.shuffle(mixed)

        data = {
            "dataset_type": "username",
            "n": len(inserted),
            "description": "Username strings for membership-query benchmark",
            "avg_bytes_per_elem": 12,
            "inserted": inserted,
            "negative_queries": negatives,      # dùng để đo FPR thực nghiệm
            "mixed_queries": mixed               # dùng để đo throughput
        }
        fname = os.path.join(OUTPUT_DIR, f"username_n{size}.json")
        with open(fname, 'w') as f:
            json.dump(data, f)

        print(f"  n={size:>9,} → {os.path.getsize(fname)/1024:>8.1f} KB  [{time.time()-t0:.2f}s]")

    # 2. Reference datasets (1 size each)
    print("\n[2/2] Generating 4-type REFERENCE datasets at N=10,000:")
    ref_n = 10_000
    refs = [
        ("username",    gen_username(ref_n),      "Username strings (~12B)", 12),
        ("url",         gen_url(ref_n),            "URL strings (~65B)",      65),
        ("integer_id",  gen_int_id(ref_n),         "Int64 IDs (8B)",          8),
        ("sha256",      gen_sha256_hash(ref_n),    "SHA-256 hex (64B)",       64),
    ]
    for name, data, desc, avg_bytes in refs:
        fname = os.path.join(OUTPUT_DIR, f"reference_{name}_n10k.json")
        with open(fname, 'w') as f:
            json.dump({
                "type": name, "description": desc,
                "avg_bytes_per_elem": avg_bytes,
                "n": len(data), "sample_10": data[:10]
            }, f, indent=2)
        print(f"  {name:<15}: {os.path.getsize(fname)/1024:.1f} KB | sample: {str(data[0])[:45]}")

    print_theory_tables()
    print(f"\n✅ All datasets saved to: {os.path.abspath(OUTPUT_DIR)}/")
    print(f"   Total files: {len(os.listdir(OUTPUT_DIR))}")
