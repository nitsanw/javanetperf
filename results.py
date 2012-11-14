#!/usr/bin/python

import csv
import sys

import perftest
import statistics
import stupidplot

results = {}


averages = []


server_names = perftest.SERVERS.keys()
server_names.sort()
for server_type in server_names:
    print server_type

    input = server_type + ".csv"
    data = open(input)
    reader = csv.reader(data)
    table = []
    for row in reader:
        table.append(row)
    data.close()

    scatter = [('Simultaneous Clients', 'Messages/s')]
    average = [('Simultaneous Clients', server_type, '-95% confidence', '+95% confidence')]
    for row in table:
        x = row[0]
        for y in row[1:]:
            scatter.append((x, y))

        stats = statistics.stats([float(f) for f in row[1:]])
        average.append((x, stats[0], stats[0]-stats[-1], stats[0]+stats[-1]))

    options = {
        'plottype': 'points',
        'key': False,
        'ylabel': scatter[0][1],
    }
    stupidplot.gnuplotTable(scatter, server_type + "-scatter.eps", options)

    averages.append(average)

options = {
    #~ 'plottype': 'points',
    #~ 'key': False,
    'ylabel': 'Messages/s',
    'errorbars': [1],
    'yformat': '%g',
    'xformat': '%g',
    'key': 'bottom right',
}
stupidplot.gnuplotTable(averages, "average.eps", options)
