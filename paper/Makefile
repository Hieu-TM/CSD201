# Makefile for compiling LaTeX research paper (arXiv style)

LATEX = pdflatex
BIBTEX = bibtex
MAIN = main

all: $(MAIN).pdf

$(MAIN).pdf: $(MAIN).tex references.bib
	$(LATEX) $(MAIN).tex
	$(BIBTEX) $(MAIN)
	$(LATEX) $(MAIN).tex
	$(LATEX) $(MAIN).tex

clean:
	rm -f *.aux *.bbl *.blg *.log *.out *.toc *.synctex.gz

view: $(MAIN).pdf
	xdg-open $(MAIN).pdf &

.PHONY: all clean view
