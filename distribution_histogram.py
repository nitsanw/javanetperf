#!/usr/bin/python

import csv
import sys

import stupidplot

d = open(sys.argv[1])
samples = []
for row in csv.reader(d):
    assert len(row) == 1
    samples.append(float(row[0]))
d.close()


histogram = stupidplot.histogram(samples, 16, 40000, 56000)
stupidplot.gnuplotTable(histogram, "histogram.eps")
