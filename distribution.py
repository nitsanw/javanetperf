#!/usr/bin/python

import csv
import os
import signal
import subprocess
import sys
import time

import perftest

TRIALS = 2
MAX_CLIENTS = 8

if __name__ == "__main__":
    table = []
    server = perftest.startServer(perftest.SERVERS['c++-epoll'])
    try:
        for i in xrange(TRIALS):
            result = perftest.runClient(MAX_CLIENTS)
            print result
            table.append([result])
    finally:
        if server.poll() is None:
            os.kill(server.pid, signal.SIGTERM)
            server.wait()

    f = open("distribution_10_c++-epoll.csv", "wb")
    writer = csv.writer(f)
    writer.writerows(table)
    f.close()
