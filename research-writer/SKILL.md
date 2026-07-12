---
name: research-writer
description: Specialized skill for writing technical research papers in LaTeX using arXiv preprint style. Use when user provides drafts notes documents pdf docx txt images or handwritten descriptions for creating structured IMRaD papers with diagrams critique suggestions and bibtex management. Triggers research paper latex arxiv preprint technical writing.
---

# Research Writer

## Overview

This skill enables creation of professional technical research papers following arXiv/bio-arXiv preprint standards. It handles input analysis, detailed planning, LaTeX generation with TikZ/PGFPlots diagrams, self-critique, improvement suggestions, bibtex integration, and version tracking in Overleaf-style project structure.

## Instructions

Always follow this workflow for research paper creation:

1. **Analyze Input**: Read all provided files (use pdf/docx skills if needed), OCR/describe handwritten notes or images. Extract key ideas, sections, data, figures.

2. **Create Detailed Plan**: Output structured plan including:
   - Title, authors, abstract outline.
   - IMRaD or appropriate sections with subsection suggestions.
   - Suggested diagrams (TikZ/PGFPlots for technical flows, architectures, results).
   - Figure/table placeholders and suggestions.
   - Bibliography strategy.

3. **Generate Project Structure**: Create Overleaf-style dir with:
   - main.tex (based on assets/template.tex)
   - arxiv.sty, Makefile
   - sections/ (intro.tex, method.tex etc.)
   - figures/, references.bib

4. **Write Content**: Produce high-quality English academic text. Use TikZ for diagrams replacing event sequences. Suggest image additions based on content evaluation.

5. **BibTeX Management**: Maintain references.bib. Auto-suggest citations in text. Use natbib style.

6. **Self-Critique & Iterate**: After each major draft:
   - Strengths/Weaknesses analysis (clarity, novelty, technical rigor, flow).
   - Specific improvement suggestions.
   - Version tracking (v1, v2 with changes summary).

7. **Compile & Deliver**: Provide Makefile commands. Output .tex files ready for Overleaf or local compile. Suggest next steps.

Use assets/ as base templates. Prefer technical precision for CS/engineering papers. Track versions by creating dated subdirs or comments.
