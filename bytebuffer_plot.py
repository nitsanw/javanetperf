#!/usr/bin/python

import sys

import dataextract
import stupidplot


LABEL_MAP = {
        "array": "byte[]",
        "heap": "ByteBuffer heap",
        "direct": "ByteBuffer direct",
        "be": "Big Endian",
        "le": "Little Endian",
}


def labelCmp(a, b):
    print a, b, LABEL_MAP[a[0]] < LABEL_MAP[b[0]]
    v = cmp(LABEL_MAP[a[0]], LABEL_MAP[b[0]])
    if v != 0:
        return v
    return cmp(a[1], b[1])


def plot(path):
    table = dataextract.readCSVTable(path)

    # Take only the little endian results
    #~ table = dataextract.select(table, [(1, "le")])
    # Express buffer size in kB
    table = [(r[0], r[1], r[2]/1024, r[3]) for r in table]
    # Express throughput in GB/s
    #~ table = [(r[0], r[1], r[2], r[3]/1024.) for r in table]
    groups = dataextract.groupBy(table, [0, 1])

    datasets = []
    keys = groups.keys()
    keys.sort(labelCmp)
    for key in keys:
        data = dataextract.selectStatsConfPlot(groups[key], [2], 3)
        label = ("Buffer Size (kB)", "%s %s" % (LABEL_MAP[key[0]], LABEL_MAP[key[1]]))
        #~ label = ("Buffer Size (kB)", LABEL_MAP[key[0]])
        print label
        data.insert(0, label)
        datasets.append(data)
        print data

    #~ data = dataextract.selectStatsConfPlot(table, [0, 1, 2], 3)
    #~ print data
    
    options = {
            "key": "bottom right",
            "errorbars": [1],
            "xrange": "[0:]",
            "yrange": "[0:]",
            #~ "ylabel": "Write Throughput (GB/s)",
            #~ "ylabel": "Fill Byte Throughput (MB/s)",
            "ylabel": "Fill Int Throughput (MB/s)",
            "xformat": "%.0f",
            #~ "xtics": "1024",
            "yformat": "%.0f",
    }
    stupidplot.gnuplotTable(datasets, "out.eps", options)

    #~ plot_datasets = []
    #~ keys = groups.keys()
    #~ keys.sort()
    #~ for key in keys:
        #~ print groups[key][0]
        #~ data = dataextract.selectStatsConfPlot(groups[key], [0, 1, 2], 3)
        #~ label = "%d CPUs" % key
        #~ data.insert(0, [X_AXIS, label, "- conf", "+ conf"])
        #~ datasets.append(data)

        #~ stupidplot.gnuplotTable(datasets, path.replace(".csv", ".eps"), options)

        #~ # For each number of threads, pick out the maximum
        #~ max_table = [["CPUs", input_label]]
        #~ for key in keys:
            #~ data = dataextract.selectStatsConfPlot(groups[key], [1], 2)


if __name__ == "__main__":
    plot(sys.argv[1])
