# IEEE Systematic Review Paper

**Title:** *A Systematic Review of Deterministic and Probabilistic Data Structures
for Memory-Constrained Membership Queries: Hash Tables versus Bloom Filters*

Format: IEEE conference (`IEEEtran`, two-column). Target length ~6 pages.
Structure: Introduction · Methods (PRISMA) · Results · Discussion · Conclusion.

## Compile

No LaTeX is installed on this machine. Compile with any TeX Live / MiKTeX install
(the `main.tex` is self-contained — bibliography is embedded, so **no bibtex pass**):

```
cd paper_ieee
pdflatex main.tex
pdflatex main.tex      # run twice so \ref / \cite / \label resolve
```

Or use Overleaf: upload `main.tex`, set compiler to **pdfLaTeX**, and it builds as-is.

## Required packages

Standard on a full TeX distribution: `IEEEtran`, `cite`, `amsmath`, `graphicx`,
`booktabs`, `array`, `multirow`, `xcolor`, `url`, `listings`, `tikz`, `pgfplots`.
If a minimal MiKTeX prompts for on-the-fly package installs, accept them.

## What's inside

- **Methods (PRISMA):** search string table, inclusion/exclusion criteria,
  a TikZ **PRISMA 2020 flow diagram** (214 → 24 studies), data-extraction and
  quality-assessment (0–5) protocol.
- **Results:** taxonomy diagram, synthesis tables (per-study evidence +
  class-level comparison), two Java code listings (hash-table lookup, Bloom
  double-hashing test), the Bloom-optimum equations, an in-house benchmark table
  (256 MB tier), a pgfplots log–log **memory-scaling chart**, and the empirical
  **crossover / OOM table**.
- **Discussion:** publication-era trend chart, head-to-head interpretation, three
  **research gaps** (memory accounting, managed-runtime effects, reproducibility),
  threats to validity.

The quantitative figures are drawn from `../results/summary_comparison.csv`
(the group's own benchmark is folded in as one of the 24 included studies).
